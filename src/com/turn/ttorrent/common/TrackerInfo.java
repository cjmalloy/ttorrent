package com.turn.ttorrent.common;

/**
 * Contains information about tracker
 * 
 * @author AnDyX
 * 
 */
public class TrackerInfo {
	public enum Status {
		CONNECTED, NOT_CONNECTED;
	}

	private final String trackerUrl;
	private long udpConnectionId;
	private Status status = Status.NOT_CONNECTED;

	public TrackerInfo(String trackerUrl) {
		this.trackerUrl = trackerUrl;
	}

	public String getTrackerUrl() {
		return trackerUrl;
	}

	public long getUdpConnectionId() {
		return udpConnectionId;
	}

	public void setUdpConnectionId(long udpConnectionId) {
		this.udpConnectionId = udpConnectionId;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}
}
