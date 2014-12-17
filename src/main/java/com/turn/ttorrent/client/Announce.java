/** Copyright (C) 2011 Turn, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.turn.ttorrent.client;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.turn.ttorrent.bcodec.BEValue;
import com.turn.ttorrent.bcodec.InvalidBEncodingException;
import com.turn.ttorrent.client.TrackerClient.Status;
import com.turn.ttorrent.client.message.TrackerMessage;
import com.turn.ttorrent.client.message.TrackerMessage.HttpTrackerMessage;

/**
 * BitTorrent client tracker announce thread.
 * 
 * <p>
 * A BitTorrent client must check-in to the torrent's tracker every now and
 * then, and when particular events happen.
 * </p>
 * 
 * <p>
 * This Announce class implements a periodic announce request thread that will
 * notify announce request event listeners for each tracker response.
 * </p>
 * 
 * @author mpetazzoni
 * @see <a
 *      href="http://wiki.theory.org/BitTorrentSpecification#Tracker_Request_Parameters">BitTorrent
 *      tracker request specification</a>
 * @see com.turn.ttorrent.client.Announce.AnnounceEvent
 */
public class Announce implements Runnable, AnnounceResponseListener {

	private static final Logger logger = LoggerFactory
			.getLogger(Announce.class);

	/** The torrent announced by this announce thread. */
	private SharedTorrent torrent;

	/** The peer ID we report to the tracker. */
	private String id;

	/** Our client address, to report our IP address and port to the tracker. */
	private InetSocketAddress address;

	/** The set of listeners to announce request answers. */
	private Set<AnnounceResponseListener> listeners;

	/** Announce thread and control. */
	private Thread thread;
	private boolean stop;
	private boolean forceStop;

	/** Announce interval, initial 'started' event control. */
	private int interval;
	private boolean initial;

	/**
	 * Announce request event types.
	 * 
	 * When the client starts exchanging on a torrent, it must contact the
	 * torrent's tracker with a 'started' announce request, which notifies the
	 * tracker this client now exchanges on this torrent (and thus allows the
	 * tracker to report the existence of this peer to other clients).
	 * 
	 * When the client stops exchanging, or when its download completes, it must
	 * also send a specific announce request. Otherwise, the client must send an
	 * eventless (NONE), periodic announce request to the tracker at an interval
	 * specified by the tracker itself, allowing the tracker to refresh this
	 * peer's status and acknowledge that it is still there.
	 */
	public enum AnnounceEvent {
		NONE(0), STARTED(2), STOPPED(3), COMPLETED(1);

		private final int id;

		private AnnounceEvent(int id) {
			this.id = id;
		}

		public int getId() {
			return id;
		}
	};

	/**
	 * Create a new announcer for the given torrent.
	 * 
	 * @param torrent
	 *            The torrent we're announing about.
	 * @param id
	 *            Our client peer ID.
	 * @param address
	 *            Our client network address, used to extract our external IP
	 *            and listening port.
	 */
	Announce(SharedTorrent torrent, String id, InetSocketAddress address) {
		this.torrent = torrent;
		this.id = id;
		this.address = address;

		this.listeners = new HashSet<AnnounceResponseListener>();
		this.thread = null;
		this.register(this);
	}

	/**
	 * Register a new announce response listener.
	 * 
	 * @param listener
	 *            The listener to register on this announcer events.
	 */
	public void register(AnnounceResponseListener listener) {
		this.listeners.add(listener);
	}

	/**
	 * Start the announce request thread.
	 */
	public void start() {
		this.stop = false;
		this.forceStop = false;

		if (this.thread == null || !this.thread.isAlive()) {
			this.thread = new Thread(this);
			this.thread.setName("bt-announce");
			this.thread.setDaemon(true);
			this.thread.start();
		}
	}

	/**
	 * Stop the announce thread.
	 * 
	 * One last 'stopped' announce event will be sent to the tracker to announce
	 * we're going away.
	 */
	public void stop() {
		this.stop = true;

		if (this.thread != null && this.thread.isAlive()) {
			this.thread.interrupt();
		}

		this.thread = null;
	}

	/**
	 * Stop the announce thread.
	 * 
	 * @param hard
	 *            Whether to force stop the announce thread or not, i.e. not
	 *            send the final 'stopped' announce request or not.
	 */
	@SuppressWarnings("unused")
	private void stop(boolean hard) {
		this.forceStop = true;
		this.stop();
	}

	/**
	 * Main announce loop.
	 * 
	 * The announce thread starts by making the initial 'started' announce
	 * request to register on the tracker and get the announce interval value.
	 * Subsequent announce requests are ordinary, event-less, periodic requests
	 * for peers.
	 * 
	 * Unless forcefully stopped, the announce thread will terminate by sending
	 * a 'stopped' announce request before stopping.
	 */
	@Override
	public void run() {
		logger.info("Starting announce thread for " + torrent.getName() + "...");

		// Set an initial announce interval to 5 seconds. This will be updated
		// in real-time by the tracker's responses to our announce requests.
		this.interval = 5;
		this.initial = true;

		while (!this.stop) {
			this.announce(this.initial ? AnnounceEvent.STARTED
					: AnnounceEvent.NONE);

			try {
				logger.trace("Sending next announce in " + this.interval
						+ " seconds.");
				Thread.sleep(this.interval * 1000);
			} catch (InterruptedException ie) {
				// Ignore
			}
		}

		if (!this.forceStop) {
			// Send the final 'stopped' event to the tracker after a little
			// while.
			try {
				Thread.sleep(500);
			} catch (InterruptedException ie) {
				// Ignore
			}

			this.announce(AnnounceEvent.STOPPED, true);
		}
	}

	/**
	 * Build, send and process a tracker announce request.
	 * 
	 * <p>
	 * This function first builds an announce request for the specified event
	 * with all the required parameters. Then, the request is made to the
	 * tracker's announce URL and the response is read and B-decoded.
	 * </p>
	 * 
	 * <p>
	 * All registered {@link AnnounceResponseListener} objects are then fired
	 * with the decoded payload.
	 * </p>
	 * 
	 * @param event
	 *            The announce event type (can be AnnounceEvent.NONE for
	 *            periodic updates).
	 * @return The decoded tracker response is also returned.
	 */
	private void announce(AnnounceEvent event) {
		this.announce(event, false);
	}

	/**
	 * Build, send and process a tracker announce request.
	 * 
	 * <p>
	 * Gives the ability to perform an announce request without notifying the
	 * registered listeners.
	 * </p>
	 * 
	 * @see #announce(AnnounceEvent event)
	 * @param event
	 *            The announce event type (can be AnnounceEvent.NONE for
	 *            periodic updates).
	 * @param inhibitEvent
	 *            Prevent event listeners from being notified.
	 * @return The decoded tracker response is also returned.
	 */
	private void announce(AnnounceEvent event, boolean inhibitEvent) {

		if (this.torrent.getTrackerList() != null)
			for (TrackerClient tracker : this.torrent.getTrackerList()) {

				try {
					try {
						logger.debug("Announcing "
								+ (!AnnounceEvent.NONE.equals(event) ? event
										.name() + " " : "") + "to tracker "
								+ tracker.getTrackerUrl() + " with "
								+ this.torrent.getUploaded() + "U/"
								+ this.torrent.getDownloaded() + "D/"
								+ this.torrent.getLeft() + "L bytes for "
								+ this.torrent.getName() + "...");

						TrackerMessage result = tracker.announce(event,
								torrent, this.id, this.address);

						tracker.setStatus(Status.CONNECTED);

						if (!inhibitEvent) {
							for (AnnounceResponseListener listener : this.listeners) {
								listener.handleAnnounceResponse(result);
							}
						}

					} catch (UnsupportedEncodingException uee) {
						logger.error("{}", uee.getMessage(), uee);
					} catch (MalformedURLException mue) {
						logger.error("{}", mue.getMessage(), mue);
					} catch (InvalidBEncodingException ibee) {
						logger.error("Error parsing tracker response: {}",
								ibee.getMessage(), ibee);
					} catch (IOException ioe) {
						logger.warn("Error reading response from tracker: {}",
								ioe.getMessage());
					}
				} catch (Exception ex) {
					logger.error(ex.toString(), ex);
				}
			}
	}

	/**
	 * Handle an announce request answer to set the announce interval.
	 */
	public void handleAnnounceResponse(TrackerMessage message) {
		try {
			if (message instanceof HttpTrackerMessage) {
				HttpTrackerMessage httpTrackerMessage = (HttpTrackerMessage) message;
				Map<String, BEValue> answer = httpTrackerMessage.getResponse();

				if (answer != null && answer.containsKey("interval")) {
					this.interval = answer.get("interval").getInt();
					this.initial = false;
				}
			}
		} catch (InvalidBEncodingException ibee) {
			//this.stop(true);
		}
	}
}
