package com.turn.ttorrent.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.turn.ttorrent.bcodec.BDecoder;
import com.turn.ttorrent.bcodec.BEValue;
import com.turn.ttorrent.bcodec.BEncoder;
import com.turn.ttorrent.client.peer.DHTPeer;
import com.turn.ttorrent.client.peer.DHTPeer.DHTPeerStatus;
import com.turn.ttorrent.common.Peer;

/**
 * Talks to DHT peers
 * 
 * @author AnDyX
 * 
 */
public class DHTClient implements Runnable {
	private static final Logger logger = LoggerFactory
			.getLogger(DHTClient.class);

	public enum DHTClientStatus {
		INITIALISING, STARTED
	};

	private byte[] dhtKey = new byte[20];

	/** DHTClient thread and control. */
	private Thread thread;
	private boolean stop;
	private final Client client;
	private final ConcurrentMap<String, DHTPeer> dhtPeers;

	private DHTClientStatus status = DHTClientStatus.INITIALISING;

	public DHTClient(Client client) {
		this.client = client;

		generateUniqueKey();

		dhtPeers = new ConcurrentHashMap<String, DHTPeer>();

		handleNewDHTPeer(new Peer("router.utorrent.com", 6881, null), 1);
		handleNewDHTPeer(new Peer("router.bittorrent.com", 6881, null), 1);

		this.status = DHTClientStatus.STARTED;
	}

	private void generateUniqueKey() {
		try {
			// Initialize SecureRandom
			// This is a lengthy operation, to be done only upon
			// initialization of the application
			SecureRandom prng = SecureRandom.getInstance("SHA1PRNG");

			// generate a random number
			String randomNum = new Long(prng.nextLong()).toString();

			// get its digest
			MessageDigest sha = MessageDigest.getInstance("SHA-1");
			byte[] result = sha.digest(randomNum.getBytes());

			// copy up to 20 bytes
			for (int i = 0; i < 20; i++)
				dhtKey[i] = result[i];
		} catch (Exception ex) {
			logger.error(ex.toString());
			new Random().nextBytes(dhtKey);
		}
	}

	/**
	 * Start the dhtClient request thread.
	 */
	public void start() {
		this.stop = false;

		if (this.thread == null || !this.thread.isAlive()) {
			this.thread = new Thread(this);
			this.thread.setDaemon(true);
			this.thread.setName("bt-dht-client");
			this.thread.start();
		}
	}

	/**
	 * Stop the dhtClient thread.
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

	@Override
	public void run() {
		logger.info("Starting dht client thread ...");

		while (!this.stop) {
			try {
				ExecutorService executor = Executors.newFixedThreadPool(1,
						new RetrievePeersThreadFactory());

				synchronized (dhtPeers) {
					for (DHTPeer peer : dhtPeers.values()) {
						executor.submit(new RetrievePeers(peer));
					}
				}

				executor.shutdown();
				while (!executor.isTerminated()) {
					if (this.stop) {
						throw new InterruptedException("Torrent data analysis "
								+ "interrupted.");
					}

					Thread.sleep(10);
				}

				Thread.sleep(60 * 1000);
			} catch (InterruptedException ie) {
				// Ignore
			}
		}
	}

	public void handleNewDHTPeer(Peer peer) {
		handleNewDHTPeer(peer, -1);
	}

	private void handleNewDHTPeer(Peer peer, int level) {
		DHTPeer p = getOrCreatePeer(null, peer.getIp(), peer.getPort());

		synchronized (p) {
			if (p.getStatus() == DHTPeerStatus.NOT_CHECKED) {
				p.setStatus(this.connectToPeer(p) ? DHTPeerStatus.OPERABLE
						: DHTPeerStatus.NOT_OPERABLE);
				if (p.getStatus() == DHTPeerStatus.OPERABLE && level > 0
						&& level <= 5) {
					retrieveNodes(p, level);
					try {
						retrievePeers(p);
					} catch (Exception e) {
						logger.error(e.toString());
					}
				}
			}
		}
	}

	private boolean connectToPeer(DHTPeer p) {
		try {
			logger.debug("Connecting to {}...", p);

			Map<String, BEValue> pingRequest = new TreeMap<String, BEValue>();
			pingRequest.put("t", new BEValue("0"));
			pingRequest.put("y", new BEValue("q"));
			pingRequest.put("q", new BEValue("ping"));

			Map<String, BEValue> pingIdRequest = new TreeMap<String, BEValue>();
			pingIdRequest.put("id", new BEValue(dhtKey));
			pingRequest.put("a", new BEValue(pingIdRequest));

			ByteArrayOutputStream os = new ByteArrayOutputStream();
			BEncoder.bencode(pingRequest, os);
			ByteBuffer response = send(p, os.toByteArray());
			BEValue pingResponse = BDecoder.bdecode(new ByteArrayInputStream(
					response.array()));

			if (pingResponse.getMap().containsKey("e"))
				throw new Exception(pingResponse.getMap().get("e").getString());

			p.setPeerId(ByteBuffer.wrap(pingResponse.getMap().get("r").getMap()
					.get("id").getBytes()));

			return true;

		} catch (Exception e) {
			logger.error(e.toString());
		}

		return false;
	}

	private void retrieveNodes(DHTPeer peer, int level) {
		try {
			logger.debug("Retrieve nodes from {}...", peer);

			Map<String, BEValue> get_peersRequest = new TreeMap<String, BEValue>();
			get_peersRequest.put("t", new BEValue("0"));
			get_peersRequest.put("y", new BEValue("q"));
			get_peersRequest.put("q", new BEValue("find_node"));

			Map<String, BEValue> get_peersARequest = new TreeMap<String, BEValue>();
			get_peersARequest.put("id", new BEValue(dhtKey));
			get_peersARequest.put("target", new BEValue(dhtKey));

			get_peersRequest.put("a", new BEValue(get_peersARequest));

			ByteArrayOutputStream os = new ByteArrayOutputStream();
			BEncoder.bencode(get_peersRequest, os);
			ByteBuffer response = send(peer, os.toByteArray());
			BEValue get_peerResponse = BDecoder
					.bdecode(new ByteArrayInputStream(response.array()));

			if (get_peerResponse.getMap().containsKey("e"))
				throw new Exception(get_peerResponse.getMap().get("e")
						.getString());

			if (get_peerResponse.getValue() instanceof Map) {
				Map<String, BEValue> responseValues = get_peerResponse.getMap();

				if (responseValues.containsKey("r")
						&& responseValues.get("r").getValue() instanceof Map) {
					responseValues = responseValues.get("r").getMap();

					byte[] value = null;

					if (responseValues.containsKey("nodes")) {
						value = responseValues.get("nodes").getBytes();
					}

					if (value != null) {
						ByteBuffer buffer = ByteBuffer.wrap(value);
						while (buffer.remaining() >= 26) {
							byte[] dhtId = new byte[20];

							buffer.get(dhtId);

							byte[] ip = new byte[4];
							buffer.get(ip);

							byte[] port = new byte[2];
							buffer.get(port);

							int p = (port[0] >= 0 ? port[0] : port[0] + 256)
									* 256
									+ (port[1] >= 0 ? port[1] : port[1] + 256);

							Peer newPeer = new Peer(InetAddress
									.getByAddress(ip).getHostAddress(), p, null);

							if (level > 0 && level < 5)
								handleNewDHTPeer(newPeer, level + 1);
						}
					}
				}
			}
		} catch (Exception e) {
			logger.error(" {}...", e.toString());
		}
	}

	private void retrievePeers(DHTPeer peer) throws Exception {
		logger.debug("Retrieve peers from {}...", peer);

		Map<String, BEValue> get_peersRequest = new TreeMap<String, BEValue>();
		get_peersRequest.put("t", new BEValue("0"));
		get_peersRequest.put("y", new BEValue("q"));
		get_peersRequest.put("q", new BEValue("get_peers"));

		Map<String, BEValue> get_peersARequest = new TreeMap<String, BEValue>();
		get_peersARequest.put("id", new BEValue(dhtKey));
		get_peersARequest.put("info_hash", new BEValue(this.client.getTorrent()
				.getInfoHash()));

		get_peersRequest.put("a", new BEValue(get_peersARequest));

		ByteArrayOutputStream os = new ByteArrayOutputStream();
		BEncoder.bencode(get_peersRequest, os);
		ByteBuffer response = send(peer, os.toByteArray());
		BEValue get_peerResponse = BDecoder.bdecode(new ByteArrayInputStream(
				response.array()));

		if (get_peerResponse.getMap().containsKey("e"))
			throw new Exception(get_peerResponse.getMap().get("e").getString());

		if (get_peerResponse.getValue() instanceof Map) {
			Map<String, BEValue> responseValues = get_peerResponse.getMap();

			if (responseValues.containsKey("r")
					&& responseValues.get("r").getValue() instanceof Map) {
				responseValues = responseValues.get("r").getMap();

				byte[] value = null;
				boolean containsDHTNodes = false;

				if (responseValues.containsKey("values")) {
					value = responseValues.get("values").getBytes();
				} else if (responseValues.containsKey("nodes")) {
					value = responseValues.get("nodes").getBytes();
					containsDHTNodes = true;
				}

				if (value != null) {
					List<Peer> result = new ArrayList<Peer>();
					ByteBuffer buffer = ByteBuffer.wrap(value);
					while (buffer.remaining() >= (containsDHTNodes ? 26 : 6)) {
						byte[] dhtId = new byte[20];

						if (containsDHTNodes)
							buffer.get(dhtId);

						byte[] ip = new byte[4];
						buffer.get(ip);

						byte[] port = new byte[2];
						buffer.get(port);

						int p = (port[0] >= 0 ? port[0] : port[0] + 256) * 256
								+ (port[1] >= 0 ? port[1] : port[1] + 256);

						Peer newPeer = new Peer(InetAddress.getByAddress(ip)
								.getHostAddress(), p, null);

						if (containsDHTNodes) {
							handleNewDHTPeer(newPeer);
						}
						// } else {
						result.add(newPeer);
						// }

					}

					client.handleAnnounceResponse(result, true);
				}
			}
		}
	}

	private ByteBuffer send(DHTPeer p, byte[] request)
			throws URISyntaxException, IOException {
		InetAddress ipAddress = InetAddress.getByName(p.getIp());
		return UDPConnectionManager.getInstance().send(ipAddress, p.getPort(),
				request);
	}

	/**
	 * Retrieve a DHTPeer object from the given peer ID, IP address and port
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
	private DHTPeer getOrCreatePeer(byte[] dhtPeerId, String ip, int port) {
		Peer search = new Peer(ip, port,
				(dhtPeerId != null ? ByteBuffer.wrap(dhtPeerId)
						: (ByteBuffer) null));
		DHTPeer peer = null;

		synchronized (this.dhtPeers) {
			peer = this.dhtPeers.get(search.hasPeerId() ? search.getHexPeerId()
					: search.getHostIdentifier());

			if (peer != null) {
				return peer;
			}

			if (search.hasPeerId()) {
				peer = this.dhtPeers.get(search.getHostIdentifier());
				if (peer != null) {
					synchronized (peer) {

						// Set peer ID for perviously known peer.
						peer.setPeerId(search.getPeerId());

						this.dhtPeers.remove(peer.getHostIdentifier());
						this.dhtPeers.putIfAbsent(peer.getHexPeerId(), peer);
						return peer;
					}
				}
			}

			peer = new DHTPeer(ip, port);
			peer.setPeerId(search.getPeerId());

			this.dhtPeers.putIfAbsent(peer.hasPeerId() ? peer.getHexPeerId()
					: peer.getHostIdentifier(), peer);
			logger.trace("Created new dht peer {}.", peer);
		}

		return peer;
	}

	public DHTClientStatus getStatus() {
		return status;
	}

	class RetrievePeers implements Runnable {
		private final DHTPeer peer;

		public RetrievePeers(DHTPeer peer) {
			this.peer = peer;
		}

		@Override
		public void run() {
			synchronized (peer) {
				try {
					retrievePeers(peer);
				} catch (Exception e) {
					logger.error(e.toString());
				}
			}
		}
	}

	static class RetrievePeersThreadFactory implements ThreadFactory {
		private static int id = 1;

		@Override
		public Thread newThread(Runnable r) {
			Thread thread = new Thread(r);
			thread.setDaemon(true);
			thread.setName("bt-dht-get-peers-" + Integer.toString(id));
			id++;
			return thread;
		}
	}
}
