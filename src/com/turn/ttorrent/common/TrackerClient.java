package com.turn.ttorrent.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.turn.ttorrent.bcodec.BDecoder;
import com.turn.ttorrent.bcodec.BEValue;
import com.turn.ttorrent.bcodec.BEncoder;
import com.turn.ttorrent.client.Announce.AnnounceEvent;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.client.message.TrackerMessage;
import com.turn.ttorrent.client.message.TrackerMessage.AnnounceUDPTrackerMessage;
import com.turn.ttorrent.client.message.TrackerMessage.HttpTrackerMessage;
import com.turn.ttorrent.client.message.TrackerMessage.UDPTrackerMessage;
import com.turn.ttorrent.client.message.TrackerMessage.UDPTrackerMessage.ConnectUDPTrackerMessage;

/**
 * Talks to trackers.
 * 
 * @author AnDyX
 * 
 */
public abstract class TrackerClient {
	private static final Logger logger = LoggerFactory
			.getLogger(TrackerClient.class);

	public enum Status {
		CONNECTED, NOT_CONNECTED;
	}

	protected final String trackerUrl;
	private Status status = Status.NOT_CONNECTED;

	public TrackerClient(String url) {
		this.trackerUrl = url;
	}

	public String getTrackerUrl() {
		return trackerUrl;
	}

	public abstract TrackerMessage announce(AnnounceEvent event,
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

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	/**
	 * Talks to Http tracker
	 * 
	 * @author AnDyX
	 * 
	 */
	public static class HttpTrackerClient extends TrackerClient {

		public HttpTrackerClient(String url) {
			super(url);
		}

		@Override
		public TrackerMessage announce(AnnounceEvent event,
				SharedTorrent torrent, String id, InetSocketAddress address)
				throws Exception {
			URL announce = this.buildAnnounceURL(this.trackerUrl,
					prepareParameters(event, torrent, id, address));
			URLConnection conn = announce.openConnection();
			InputStream is = conn.getInputStream();
			Map<String, BEValue> result = BDecoder.bdecode(is).getMap();
			is.close();
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			BEncoder.bencode(result, os);
			os.close();
			return HttpTrackerMessage.parse(ByteBuffer.wrap(os.toByteArray()),
					torrent);
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

	/**
	 * Talks to Udp tracker
	 * 
	 * @author AnDyX
	 * 
	 */
	public static class UdpTrackerClient extends TrackerClient {
		private long connectionId;

		public long getConnectionId() {
			return connectionId;
		}

		public void setConnectionId(long connectionId) {
			this.connectionId = connectionId;
		}

		public UdpTrackerClient(String url) {
			super(url);
		}

		@Override
		public TrackerMessage announce(AnnounceEvent event,
				SharedTorrent torrent, String id, InetSocketAddress address)
				throws Exception {

			if (getStatus() != Status.CONNECTED) {
				byte[] request = ConnectUDPTrackerMessage.craft().getData()
						.array();
				TrackerMessage message = UDPTrackerMessage.parse(send(request),
						torrent);
				if (message instanceof ConnectUDPTrackerMessage) {
					this.setConnectionId(((ConnectUDPTrackerMessage) message)
							.getConnectionId());
				} else
					throw new Exception("Invalid response");
			}

			byte[] request = AnnounceUDPTrackerMessage
					.craft(event, getConnectionId(), torrent, id, address)
					.getData().array();
			TrackerMessage message = UDPTrackerMessage.parse(send(request),
					torrent);

			return message;
		}

		private ByteBuffer send(byte[] request) throws URISyntaxException,
				IOException {
			byte[] receiveData = new byte[1024];

			URI uri = new URI(this.trackerUrl);
			DatagramSocket clientSocket = new DatagramSocket();
			clientSocket.setSoTimeout(10 * 1000);
			InetAddress IPAddress = InetAddress.getByName(uri.getHost());

			DatagramPacket sendPacket = new DatagramPacket(request,
					request.length, IPAddress, uri.getPort());
			clientSocket.send(sendPacket);
			DatagramPacket receivePacket = new DatagramPacket(receiveData,
					receiveData.length);
			clientSocket.receive(receivePacket);

			ByteBuffer result = ByteBuffer.wrap(receivePacket.getData(), 0,
					receivePacket.getLength());
			clientSocket.close();

			return result;
		}
	}
}
