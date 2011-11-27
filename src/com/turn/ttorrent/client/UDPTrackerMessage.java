package com.turn.ttorrent.client;

import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.Random;

/**
 * Parse and create UDP Tracker messages
 * 
 * @author AnDyX
 * 
 */
public class UDPTrackerMessage {
	private static final long UDP_CONNECTION_MAGIC = 0x41727101980L;

	public enum UDPType {
		CONNECT(0), ANNOUNCE(1), SCRAPE(2), ERROR(3);

		private int id;

		UDPType(int id) {
			this.id = id;
		}

		public boolean equals(char c) {
			return this.id == c;
		}

		public byte getTypeByte() {
			return (byte) this.id;
		}

		public static UDPType get(char c) {
			for (UDPType t : UDPType.values()) {
				if (t.equals(c)) {
					return t;
				}
			}
			return null;
		}
	}

	private UDPType type;
	private ByteBuffer data;
	private int transactionId;
	private long connectionId;
	private final static Random transactionIdGenerator = new Random();

	private UDPTrackerMessage(UDPType type, ByteBuffer data, long connectionId,
			int transactionId) {
		this.type = type;
		this.data = data;
		this.connectionId = connectionId;
		this.transactionId = transactionId;
		this.data.rewind();
	}

	public UDPType getType() {
		return this.type;
	}

	public ByteBuffer getData() {
		return this.data;
	}

	public int getTransactionId() {
		return transactionId;
	}

	public long getConnectionId() {
		return connectionId;
	}

	/**
	 * Validate that this message makes sense for the torrent it's related to.
	 * 
	 * This method is meant to be overloaded by distinct message types, where it
	 * makes sense. Otherwise, it defaults to true.
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
			SharedTorrent torrent) throws ParseException {
		buffer.rewind();

		UDPType type = UDPType.get((char) buffer.getInt());
		if (type == null) {
			throw new ParseException("Unknown message ID!",
					buffer.position() - 1);
		}

		switch (type) {

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

	public static class ConnectUDPMessage extends UDPTrackerMessage {
		static final int BASE_SIZE = 16;

		public ConnectUDPMessage(ByteBuffer buffer, long connectionId,
				int transactionId) {
			super(UDPType.CONNECT, buffer, connectionId, transactionId);
		}

		public static ConnectUDPMessage parse(ByteBuffer buffer,
				SharedTorrent torrent) throws MessageValidationException {
			buffer.rewind();
			buffer.getInt();
			return (ConnectUDPMessage) new ConnectUDPMessage(buffer,
					buffer.getLong(), buffer.getInt()).validate(torrent);
		}

		public static ConnectUDPMessage craft() {
			int trId = UDPTrackerMessage.generateTransactionId();
			ByteBuffer buffer = ByteBuffer
					.allocate(ConnectUDPMessage.BASE_SIZE);
			buffer.putLong(UDP_CONNECTION_MAGIC);
			buffer.putInt(UDPType.CONNECT.getTypeByte());
			buffer.putInt(trId);
			return new ConnectUDPMessage(buffer, UDP_CONNECTION_MAGIC, trId);
		}
	}
}
