package com.turn.ttorrent.client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;

/**
 * Provides UDP connection to hosts.
 * 
 * @author AnDyX
 * 
 */
public class UDPConnectionManager {
	private static UDPConnectionManager instance = null;
	private final DatagramSocket clientSocket;

	private UDPConnectionManager(int port) throws SocketException {
		clientSocket = new DatagramSocket(port);
		clientSocket.setSoTimeout(60 * 1000);
	}

	public synchronized ByteBuffer send(InetAddress IPAddress, int port,
			byte[] request) throws URISyntaxException, IOException {
		byte[] receiveData = new byte[65535];

		DatagramPacket sendPacket = new DatagramPacket(request, request.length,
				IPAddress, port);
		clientSocket.send(sendPacket);
		DatagramPacket receivePacket = new DatagramPacket(receiveData,
				receiveData.length);
		clientSocket.receive(receivePacket);

		ByteBuffer result = ByteBuffer.wrap(receivePacket.getData(), 0,
				receivePacket.getLength());

		return result;
	}

	public static UDPConnectionManager init(int port) throws SocketException {
		if (instance == null) {
			instance = new UDPConnectionManager(port);
		}

		return instance;
	}

	public static UDPConnectionManager getInstance() {
		return instance;
	}
}
