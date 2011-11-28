package com.turn.ttorrent.client.message;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.turn.ttorrent.bcodec.BDecoder;
import com.turn.ttorrent.bcodec.BEValue;
import com.turn.ttorrent.bcodec.InvalidBEncodingException;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.client.Announce.AnnounceEvent;
import com.turn.ttorrent.common.Peer;
import com.turn.ttorrent.common.Torrent;

/**
 * BitTorrent tracker protocol messages representations.
 * 
 * @author AnDyX
 * 
 */
public abstract class TrackerMessage {
	private static final Logger logger = LoggerFactory
			.getLogger(TrackerMessage.class);

	public enum Type {
		CONNECT(0), ANNOUNCE(1), SCRAPE(2), ERROR(3), UNKNOWN(-1);

		private int id;

		Type(int id) {
			this.id = id;
		}

		public boolean equals(char c) {
			return this.id == c;
		}

		public byte getTypeByte() {
			return (byte) this.id;
		}

		public static Type get(char c) {
			for (Type t : Type.values()) {
				if (t.equals(c)) {
					return t;
				}
			}
			return null;
		}
	}

	private Type type;
	private ByteBuffer data;

	private TrackerMessage(Type type, ByteBuffer data) {
		this.type = type;
		this.data = data;
		if (this.data != null)
			this.data.rewind();
	}

	public Type getType() {
		return this.type;
	}

	public ByteBuffer getData() {
		return this.data;
	}

	public interface IAnnounceTrackerMessage {
		List<Peer> getPeers();
	}

	public interface IErrorTrackerMessage {
		String getErrorMessage();
	}

	/**
	 * Parse HTTP Tracker messages
	 * 
	 * @author AnDyX
	 * 
	 */
	public static class HttpTrackerMessage extends TrackerMessage {
		private final Map<String, BEValue> response;

		public HttpTrackerMessage(Type type, ByteBuffer data,
				Map<String, BEValue> response) {
			super(type, data);
			this.response = response;
		}

		public Map<String, BEValue> getResponse() {
			return response;
		}

		public static HttpTrackerMessage parse(ByteBuffer buffer,
				SharedTorrent torrent) throws InvalidBEncodingException,
				IOException {
			ByteArrayInputStream is = new ByteArrayInputStream(buffer.array());
			Map<String, BEValue> decoded = BDecoder.bdecode(is).getMap();

			if (decoded.containsKey("peers")) {
				return AnnounceHttpTrackerMessage.parse(buffer, decoded,
						torrent);
			} else if (decoded.containsKey("failure reason")) {
				return ErrorHttpTrackerMessage.parse(buffer, decoded, torrent);
			}

			return new HttpTrackerMessage(Type.UNKNOWN, buffer, decoded);
		}
	}

	public static class AnnounceHttpTrackerMessage extends HttpTrackerMessage
			implements IAnnounceTrackerMessage {
		private final List<Peer> peers;

		public AnnounceHttpTrackerMessage(ByteBuffer data, List<Peer> peers,
				Map<String, BEValue> response) {
			super(Type.ANNOUNCE, data, response);
			this.peers = peers;
		}

		@Override
		public List<Peer> getPeers() {
			return peers;
		}

		public static HttpTrackerMessage parse(ByteBuffer buffer,
				Map<String, BEValue> answer, SharedTorrent torrent) {
			List<Peer> result = new ArrayList<Peer>();
			try {
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

			return new AnnounceHttpTrackerMessage(buffer, result, answer);
		}

	}

	public static class ErrorHttpTrackerMessage extends HttpTrackerMessage
			implements IErrorTrackerMessage {
		private final String errorMessage;

		public ErrorHttpTrackerMessage(ByteBuffer data,
				Map<String, BEValue> response, String errorMessage) {
			super(Type.ERROR, data, response);
			this.errorMessage = errorMessage;
		}

		@Override
		public String getErrorMessage() {
			return errorMessage;
		}

		public static HttpTrackerMessage parse(ByteBuffer buffer,
				Map<String, BEValue> answer, SharedTorrent torrent) {
			String errorMessage = "Tracker failed";

			if (answer != null && answer.containsKey("failure reason")) {
				try {
					errorMessage = answer.get("failure reason").getString();
					logger.warn("{}", errorMessage);
				} catch (InvalidBEncodingException ibee) {
					logger.warn("Announce error, and couldn't parse "
							+ "failure reason!");
				}
			}
			return new ErrorHttpTrackerMessage(buffer, answer, errorMessage);
		}
	}

	/**
	 * Parse and create UDP Tracker messages
	 * 
	 * @author AnDyX
	 * 
	 */
	public static class UDPTrackerMessage extends TrackerMessage {
		private static final long UDP_CONNECTION_MAGIC = 0x41727101980L;

		private ByteBuffer data;
		private int transactionId;
		private long connectionId;
		private final static Random transactionIdGenerator = new Random();

		private UDPTrackerMessage(Type type, ByteBuffer data,
				long connectionId, int transactionId) {
			super(type, data);
			this.connectionId = connectionId;
			this.transactionId = transactionId;
			if (this.data != null)
				this.data.rewind();
		}

		public int getTransactionId() {
			return transactionId;
		}

		public long getConnectionId() {
			return connectionId;
		}

		/**
		 * Validate that this message makes sense for the torrent it's related
		 * to.
		 * 
		 * This method is meant to be overloaded by distinct message types,
		 * where it makes sense. Otherwise, it defaults to true.
		 * 
		 * @param torrent
		 *            The torrent this message is about.
		 */
		public UDPTrackerMessage validate(SharedTorrent torrent)
				throws MessageValidationException {
			return this;
		}

		public String toString() {
			return this.getType().name();
		}

		/**
		 * Parse the given buffer into a peer protocol Message.
		 * 
		 * Parses the provided byte array and builds the corresponding Message
		 * subclass object.
		 * 
		 * @param buffer
		 *            The byte buffer containing the message data.
		 * @param torrent
		 *            The torrent this message is about.
		 * @return A Message subclass instance.
		 * @throws ParseException
		 *             When the message is invalid, can't be parsed or does not
		 *             match the protocol requirements.
		 */
		public static UDPTrackerMessage parse(ByteBuffer buffer,
				SharedTorrent torrent) throws Exception {
			buffer.rewind();

			Type type = Type.get((char) buffer.getInt());
			if (type == null) {
				throw new ParseException("Unknown message ID!",
						buffer.position() - 1);
			}

			switch (type) {
			case CONNECT:
				return ConnectUDPTrackerMessage.parse(buffer, torrent);
			case ANNOUNCE:
				return AnnounceUDPTrackerMessage.parse(buffer, torrent);
			case ERROR:
				return ErrorUDPTrackerMessage.parse(buffer, torrent);
			case SCRAPE:
			default:
				throw new IllegalStateException("Message type should have "
						+ "been properly defined by now.");
			}
		}

		public static int generateTransactionId() {
			return transactionIdGenerator.nextInt();
		}

		public static class MessageValidationException extends ParseException {
			static final long serialVersionUID = -1;

			public MessageValidationException(Message m) {
				super("Message " + m + " is not valid!", 0);
			}

		}

		public static class ConnectUDPTrackerMessage extends UDPTrackerMessage {
			static final int BASE_SIZE = 16;

			public ConnectUDPTrackerMessage(ByteBuffer buffer,
					long connectionId, int transactionId) {
				super(Type.CONNECT, buffer, connectionId, transactionId);
			}

			public static ConnectUDPTrackerMessage parse(ByteBuffer buffer,
					SharedTorrent torrent) throws MessageValidationException {
				buffer.rewind();
				buffer.getInt();
				return (ConnectUDPTrackerMessage) new ConnectUDPTrackerMessage(
						buffer, buffer.getLong(), buffer.getInt())
						.validate(torrent);
			}

			public static ConnectUDPTrackerMessage craft() {
				int trId = UDPTrackerMessage.generateTransactionId();
				ByteBuffer buffer = ByteBuffer
						.allocate(ConnectUDPTrackerMessage.BASE_SIZE);
				buffer.putLong(UDP_CONNECTION_MAGIC);
				buffer.putInt(Type.CONNECT.getTypeByte());
				buffer.putInt(trId);
				return new ConnectUDPTrackerMessage(buffer,
						UDP_CONNECTION_MAGIC, trId);
			}
		}
	}

	public static class AnnounceUDPTrackerMessage extends UDPTrackerMessage
			implements IAnnounceTrackerMessage {
		static final int BASE_SIZE = 98;
		private final List<Peer> peers;

		public AnnounceUDPTrackerMessage(ByteBuffer buffer, long connectionId,
				int transactionId, List<Peer> peers) {
			super(Type.ANNOUNCE, buffer, connectionId, transactionId);
			this.peers = peers;
		}

		@SuppressWarnings("unused")
		public static AnnounceUDPTrackerMessage parse(ByteBuffer buffer,
				SharedTorrent torrent) throws Exception {
			List<Peer> result = new ArrayList<Peer>();

			buffer.rewind();
			buffer.getInt();

			int transactionId = buffer.getInt();
			int interval = buffer.getInt();
			int leechers = buffer.getInt();
			int seeders = buffer.getInt();

			while (buffer.remaining() > 6) {
				byte[] ip = new byte[4];
				buffer.get(ip);
				int port = buffer.getShort();
				if (port < 0)
					port += 65536;
				Peer peer = new Peer(InetAddress.getByAddress(ip).toString(),
						port, null);
				result.add(peer);
			}

			return new AnnounceUDPTrackerMessage(buffer, 0, transactionId,
					result);
		}

		public static AnnounceUDPTrackerMessage craft(AnnounceEvent event,
				long connectionId, SharedTorrent torrent, String id,
				InetSocketAddress address) {
			int transactionId = UDPTrackerMessage.generateTransactionId();
			ByteBuffer buffer = ByteBuffer
					.allocate(AnnounceUDPTrackerMessage.BASE_SIZE);
			buffer.putLong(connectionId);
			buffer.putInt(Type.ANNOUNCE.getTypeByte());
			buffer.putInt(transactionId);

			// info hash
			for (int i = 0; i < 20; i++) {
				buffer.put(torrent.getInfoHash().length > i ? torrent
						.getInfoHash()[i] : 0);
			}

			// peer id
			try {
				byte[] peerIdByte = id.getBytes(Torrent.BYTE_ENCODING);
				for (int i = 0; i < 20; i++) {
					buffer.put(peerIdByte.length > i ? peerIdByte[i] : 0);
				}
			} catch (UnsupportedEncodingException uee) {
				logger.warn("{}", uee.getMessage());
				for (int i = 0; i < 20; i++) {
					buffer.put((byte) 0);
				}
			}

			buffer.putLong(torrent.getDownloaded());
			buffer.putLong(torrent.getLeft());
			buffer.putLong(torrent.getUploaded());
			buffer.putInt(event.getId());

			byte[] add = address.getAddress().getAddress();
			buffer.put(add);

			buffer.putInt(0);
			buffer.putInt(100);
			buffer.putShort((short) address.getPort());

			return new AnnounceUDPTrackerMessage(buffer, connectionId,
					transactionId, null);
		}

		@Override
		public List<Peer> getPeers() {
			return peers;
		}

	}

	public static class ErrorUDPTrackerMessage extends UDPTrackerMessage
			implements IErrorTrackerMessage {
		private final String errorMessage;

		public ErrorUDPTrackerMessage(ByteBuffer buffer, long connectionId,
				int transactionId, String errorMessage) {
			super(Type.ERROR, buffer, connectionId, transactionId);
			this.errorMessage = errorMessage;
		}

		@Override
		public String getErrorMessage() {
			return errorMessage;
		}

		public static ErrorUDPTrackerMessage parse(ByteBuffer buffer,
				SharedTorrent torrent) throws MessageValidationException {
			buffer.rewind();
			buffer.getInt();
			int transactionId = buffer.getInt();
			String errorMessage = "Tracker failed";
			try {
				errorMessage = new String(buffer.array(), 8,
						buffer.array().length - 8);
				logger.warn("{}", errorMessage);
			} catch (Exception e) {
				logger.error("{}", e.toString());
			}

			return new ErrorUDPTrackerMessage(buffer, 0, transactionId,
					errorMessage);
		}
	}
}
