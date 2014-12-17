package com.turn.ttorrent.client.peer;

import com.turn.ttorrent.common.Peer;

public class DHTPeer extends Peer {
	public enum DHTPeerStatus {
		NOT_CHECKED, OPERABLE, NOT_OPERABLE
	}

	private DHTPeerStatus status = DHTPeerStatus.NOT_CHECKED;

	public DHTPeer(String ip, int port) {
		super(ip, port, null);
	}

	public DHTPeerStatus getStatus() {
		return status;
	}

	public void setStatus(DHTPeerStatus status) {
		this.status = status;
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder("DHT peer udp://").append(getIp())
				.append(":").append(getPort()).append("/");

		if (this.getHexPeerId() != null) {
			s.append(getHexPeerId());
		} else {
			s.append("?");
		}

		return s.toString();
	}
}
