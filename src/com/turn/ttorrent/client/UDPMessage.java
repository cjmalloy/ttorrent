package com.turn.ttorrent.client;


/**
 * Parse and create UDP Tracker messages
 * 
 * @author AnDyX
 * 
 */
public class UDPMessage {
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

	
	
}
