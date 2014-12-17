package com.turn.ttorrent.client.peer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.client.peer.SharingPeer.PeerStatus;
import com.turn.ttorrent.common.Peer;

/**
 * Manages shared peers with synchronized access.
 * 
 * @author AnDyX
 * 
 */
public class SharingPeersManager {
	private static final Logger logger = LoggerFactory
			.getLogger(SharingPeersManager.class);

	private final Object peersLockObject = new Object();

	private final TreeMap<String, SharingPeer> peersMap;
	private final TreeSet<SharingPeer> peers;
	private final SharedTorrent torrent;

	public SharingPeersManager(SharedTorrent torrent) {
		this.peersMap = new TreeMap<String, SharingPeer>();
		this.peers = new TreeSet<SharingPeer>(new SharingPeerComparator());
		this.torrent = torrent;

	}

	/**
	 * Returns n first peers on peers list that is not connected. Return null if
	 * all peers are connected or list is empty.
	 * 
	 * @return
	 */
	public List<SharingPeer> getFirstNotConnectedPeers(int count) {
		synchronized (this.peersLockObject) {
			List<SharingPeer> peersToConnect = new ArrayList<SharingPeer>();
			for (int i = 0; i < count; i++) {
				SharingPeer peer = getFirstNotConnectedPeer();
				if (peer != null) {
					peersToConnect.add(peer);
				}
			}

			for (SharingPeer peer : peersToConnect) {
				updatePeer(peer);
			}

			return peersToConnect;
		}
	}

	/**
	 * Returns first peer on peers list that is not connected. Return null if
	 * all peers are connected or list is empty. Note: it removes peer from
	 * collection - so peer need to be added again.
	 * 
	 * @return
	 */
	private SharingPeer getFirstNotConnectedPeer() {
		SharingPeer peer = null;

		synchronized (this.peersLockObject) {
			if (this.peers.size() > 0) {
				SharingPeer temp = this.peers.first();
				if (temp.getPeerStatus() != PeerStatus.CONNECTED) {
					if (temp.getPeerStatus() == PeerStatus.CONNECTION_FAILED
							&& temp.getLastPeerActivityTime()
									.compareTo(
											new Date(
													System.currentTimeMillis() - 15 * 60 * 1000)) >= 0) {
						return null;
					}
					peer = this.peers.pollFirst();
					peersMap.remove(peer.hasPeerId() ? peer.getHexPeerId()
							: peer.getHostIdentifier());
				}
			}
		}

		return peer;
	}

	public void updatePeer(SharingPeer peer) {
		synchronized (this.peersLockObject) {
			if (this.peers.contains(peer))
				this.peers.remove(peer);
			this.peers.add(peer);

			peersMap.put(
					peer.hasPeerId() ? peer.getHexPeerId() : peer
							.getHostIdentifier(), peer);
		}
	}

	/**
	 * Retrieve a SharingPeer object from the given peer ID, IP address and port
	 * number.
	 * 
	 * This function tries to retrieve an existing peer object based on the
	 * provided peer ID, or IP+Port if no peer ID is known, or otherwise
	 * instantiates a new one and adds it to our peer repository.
	 * 
	 * @param peerId
	 *            The byte-encoded string containing the peer ID. It will be
	 *            converted to its hexadecimal representation to lookup the peer
	 *            in the repository.
	 * @param ip
	 *            The peer IP address.
	 * @param port
	 *            The peer listening port number.
	 */
	public SharingPeer getOrCreatePeer(byte[] peerId, String ip, int port) {
		Peer search = new Peer(ip, port,
				(peerId != null ? ByteBuffer.wrap(peerId) : (ByteBuffer) null));
		SharingPeer peer = null;

		synchronized (this.peersLockObject) {
			peer = this.peersMap.get(search.hasPeerId() ? search.getHexPeerId()
					: search.getHostIdentifier());

			if (peer != null) {
				return peer;
			}

			if (search.hasPeerId()) {
				peer = this.peersMap.get(search.getHostIdentifier());

				if (peer != null) {
					// Set peer ID for perviously known peer.
					synchronized (peer) {
						peer.setPeerId(search.getPeerId());
					}

					this.peersMap.remove(peer.getHostIdentifier());
					this.peersMap.put(peer.getHexPeerId(), peer);
					return peer;
				}
			}

			peer = new SharingPeer(ip, port, search.getPeerId(), this.torrent);
			this.peersMap.put(
					peer.hasPeerId() ? peer.getHexPeerId() : peer
							.getHostIdentifier(), peer);
			this.peers.add(peer);
			logger.trace("Created new peer {}.", peer);
		}

		return peer;
	}

	public int peersCount() {
		synchronized (this.peersLockObject) {
			return this.peers.size();
		}
	}

	class SharingPeerComparator implements Comparator<SharingPeer> {
		@Override
		public int compare(SharingPeer o1, SharingPeer o2) {
			if (o1 == null)
				return -1;
			else if (o2 == null)
				return 1;

			if (o1 == o2)
				return 0;

			// compare status
			if (o1.getPeerStatus() != o2.getPeerStatus()) {
				if (o1.getPeerStatus() == PeerStatus.NOT_CHECKED)
					return 1;

				if (o2.getPeerStatus() == PeerStatus.NOT_CHECKED)
					return -1;

				if (o1.getPeerStatus() == PeerStatus.CONNECTED)
					return -1;

				if (o2.getPeerStatus() == PeerStatus.CONNECTED)
					return 1;
			}

			// check dht status
			if (o1.isFromDHTClient() != o2.isFromDHTClient()) {
				return o1.isFromDHTClient() ? 1 : -1;
			}

			// check dates
			int result = o1.getLastPeerActivityTime().compareTo(
					o2.getLastPeerActivityTime());

			// check peer names
			if (result == 0) {
				result = Integer.valueOf(o1.hashCode())
						.compareTo(o2.hashCode());
			}

			return result;
		}
	}
}
