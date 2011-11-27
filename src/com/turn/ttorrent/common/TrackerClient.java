package com.turn.ttorrent.common;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.turn.ttorrent.bcodec.BDecoder;
import com.turn.ttorrent.bcodec.BEValue;
import com.turn.ttorrent.bcodec.InvalidBEncodingException;
import com.turn.ttorrent.client.Announce.AnnounceEvent;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.client.UDPTrackerMessage.ConnectUDPMessage;

/**
 * Talks to trackers.
 * 
 * @author AnDyX
 * 
 */
public abstract class TrackerClient {
	private static final Logger logger = LoggerFactory
			.getLogger(TrackerClient.class);

	protected final String trackerUrl;

	public TrackerClient(String url) {
		this.trackerUrl = url;
	}

	public abstract List<Peer> announce(AnnounceEvent event,
			SharedTorrent torrent, String id, InetSocketAddress address)
			throws Exception;

	public static TrackerClient getTrackerClient(String url)
			throws URISyntaxException {
		URI uri = new URI(url);
		if (uri.getScheme().equals("udp"))
			return new UdpTrackerClient(url);
		else
			return new HttpTrackerClient(url);
	}

	public static class HttpTrackerClient extends TrackerClient {

		public HttpTrackerClient(String url) {
			super(url);
		}

		@Override
		public List<Peer> announce(AnnounceEvent event, SharedTorrent torrent,
				String id, InetSocketAddress address) throws Exception {
			URL announce = this.buildAnnounceURL(this.trackerUrl,
					prepareParameters(event, torrent, id, address));
			URLConnection conn = announce.openConnection();
			InputStream is = conn.getInputStream();
			Map<String, BEValue> result = BDecoder.bdecode(is).getMap();
			is.close();
			return handleAnnounceResponse(result);
		}

		private List<Peer> handleAnnounceResponse(Map<String, BEValue> answer)
				throws Exception {
			List<Peer> result = new ArrayList<Peer>();

			if (answer != null && answer.containsKey("failure reason")) {
				try {
					logger.warn("{}", answer.get("failure reason").getString());
				} catch (InvalidBEncodingException ibee) {
					logger.warn("Announce error, and couldn't parse "
							+ "failure reason!");
				}

				throw new Exception("Tracker failed");
			}

			try {
				if (!answer.containsKey("peers")) {
					// No peers returned by the tracker. Apparently we're alone
					// on
					// this one for now.
					return result;
				}

				try {
					List<BEValue> peers = answer.get("peers").getList();

					logger.debug("Got tracker response with {} peer(s).",
							peers.size());
					for (BEValue peerInfo : peers) {
						Map<String, BEValue> info = peerInfo.getMap();

						try {
							byte[] peerId = info.get("peer id").getBytes();
							String ip = new String(info.get("ip").getBytes(),
									Torrent.BYTE_ENCODING);
							int port = info.get("port").getInt();

							result.add(new Peer(ip, port, ByteBuffer
									.wrap(peerId)));
						} catch (NullPointerException npe) {
							throw new ParseException("Missing field from peer "
									+ "information in tracker response!", 0);
						}
					}
				} catch (InvalidBEncodingException ibee) {
					byte[] data = answer.get("peers").getBytes();
					int nPeers = data.length / 6;
					if (data.length % 6 != 0) {
						throw new InvalidBEncodingException("Invalid peers "
								+ "binary information string!");
					}

					ByteBuffer peers = ByteBuffer.wrap(data);
					logger.debug(
							"Got compact tracker response with {} peer(s).",
							nPeers);

					for (int i = 0; i < nPeers; i++) {
						byte[] ipBytes = new byte[4];
						peers.get(ipBytes);
						String ip = InetAddress.getByAddress(ipBytes)
								.getHostAddress();
						int port = (0xFF & (int) peers.get()) << 8
								| (0xFF & (int) peers.get());
						result.add(new Peer(ip, port, null));
					}
				}

			} catch (UnknownHostException uhe) {
				logger.warn("Invalid compact tracker response!", uhe);
			} catch (ParseException pe) {
				logger.warn("Invalid tracker response!", pe);
			} catch (InvalidBEncodingException ibee) {
				logger.warn("Invalid tracker response!", ibee);
			} catch (UnsupportedEncodingException uee) {
				logger.error("{}", uee.getMessage(), uee);
			}

			return result;
		}

		private Map<String, String> prepareParameters(AnnounceEvent event,
				SharedTorrent torrent, String id, InetSocketAddress address) {
			Map<String, String> params = new HashMap<String, String>();

			try {
				params.put("info_hash", new String(torrent.getInfoHash(),
						Torrent.BYTE_ENCODING));

				// Also throw in there the hex-encoded info-hash for easier
				// debugging of announce requests.
				params.put("info_hash_hex",
						Torrent.toHexString(params.get("info_hash")));
			} catch (UnsupportedEncodingException uee) {
				logger.warn("{}", uee.getMessage());
			}

			params.put("peer_id", id);
			params.put("port", new Integer(address.getPort()).toString());
			params.put("uploaded", new Long(torrent.getUploaded()).toString());
			params.put("downloaded",
					new Long(torrent.getDownloaded()).toString());
			params.put("left", new Long(torrent.getLeft()).toString());

			if (!AnnounceEvent.NONE.equals(event)) {
				params.put("event", event.name().toLowerCase());
			}

			params.put("ip", address.getAddress().getHostAddress());
			params.put("compact", "1");

			return params;
		}

		/**
		 * Build the announce request URL from the provided parameters.
		 * 
		 * @param params
		 *            The key/value parameters pairs in a map.
		 * @return The URL object representing the announce request URL.
		 */
		private URL buildAnnounceURL(String serverUrl,
				Map<String, String> params)
				throws UnsupportedEncodingException, MalformedURLException {
			StringBuilder url = new StringBuilder(serverUrl);

			if (params.size() != 0) {
				url.append("?");
			}

			for (Map.Entry<String, String> param : params.entrySet()) {
				url.append(param.getKey())
						.append("=")
						.append(URLEncoder.encode(param.getValue(),
								Torrent.BYTE_ENCODING)).append("&");
			}

			return new URL(url.toString().substring(0, url.length() - 1));
		}

	}

	public static class UdpTrackerClient extends TrackerClient {

		public UdpTrackerClient(String url) {
			super(url);
		}

		@Override
		public List<Peer> announce(AnnounceEvent event, SharedTorrent torrent,
				String id, InetSocketAddress address) throws Exception {
			URI uri = new URI(this.trackerUrl);

			byte[] receiveData = new byte[1024];
			DatagramSocket clientSocket = new DatagramSocket();
			clientSocket.setSoTimeout(10 * 1000);
			InetAddress IPAddress = InetAddress.getByName(uri.getHost());

			byte[] request = ConnectUDPMessage.craft().getData().array();

			DatagramPacket sendPacket = new DatagramPacket(request,
					request.length, IPAddress, uri.getPort());
			clientSocket.send(sendPacket);
			DatagramPacket receivePacket = new DatagramPacket(receiveData,
					receiveData.length);
			clientSocket.receive(receivePacket);
			ByteArrayInputStream is = new ByteArrayInputStream(
					receivePacket.getData());

			clientSocket.close();
			return null;
		}
	}
}
