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

}
