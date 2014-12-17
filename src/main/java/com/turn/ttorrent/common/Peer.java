/**
 * Copyright (C) 2011-2012 Turn, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.turn.ttorrent.common;

import com.turn.ttorrent.common.Torrent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;


/**
 * A basic BitTorrent peer.
 *
 * <p>
 * This class is meant to be a common base for the tracker and client, which
 * would presumably subclass it to extend its functionality and fields.
 * </p>
 *
 * @author mpetazzoni
 */
public class Peer {

	private final InetSocketAddress address;

	protected String[][] clientNameList = { { "AG", "Ares" }, { "A~", "Ares" },
			{ "AR", "Arctic" }, { "AT", "Artemis" }, { "AX", "BitPump" },
			{ "AZ", "Azureus" }, { "BB", "BitBuddy" }, { "BC", "BitComet" },
			{ "BF", "Bitflu" }, { "BG", "BTG (uses Rasterbar libtorrent)" },
			{ "BL", "BitBlinder" },
			{ "BP", "BitTorrent Pro (Azureus + spyware)" },
			{ "BR", "BitRocket" }, { "BS", "BTSlave" }, { "BW", "BitWombat" },
			{ "BX", "~Bittorrent X" }, { "CD", "Enhanced CTorrent" },
			{ "CT", "CTorrent" }, { "DE", "DelugeTorrent" },
			{ "DP", "Propagate Data Client" }, { "EB", "EBit" },
			{ "ES", "electric sheep" }, { "FC", "FileCroc" },
			{ "FT", "FoxTorrent" }, { "GS", "GSTorrent" }, { "HK", "Hekate" },
			{ "HL", "Halite" }, { "HM", "hMule (uses Rasterbar libtorrent)" },
			{ "HN", "Hydranode" }, { "KG", "KGet" }, { "KT", "KTorrent" },
			{ "LC", "LeechCraft" }, { "LH", "LH-ABC" }, { "LP", "Lphant" },
			{ "LT", "libtorrent" }, { "lt", "libTorrent" },
			{ "LW", "LimeWire" }, { "MK", "Meerkat" }, { "MO", "MonoTorrent" },
			{ "MP", "MooPolice" }, { "MR", "Miro" },
			{ "MT", "MoonlightTorrent" }, { "NX", "Net Transport" },
			{ "OS", "OneSwarm" }, { "OT", "OmegaTorrent" }, { "PD", "Pando" },
			{ "PT", "PHPTracker" }, { "qB", "qBittorrent" },
			{ "QD", "QQDownload" }, { "QT", "Qt 4 Torrent example" },
			{ "RT", "Retriever" }, { "RZ", "RezTorrent" },
			{ "S~", "Shareaza alpha/beta" }, { "SB", "~Swiftbit" },
			{ "SD", "Thunder (aka XùnLéi)" }, { "SM", "SoMud" },
			{ "SS", "SwarmScope" }, { "ST", "SymTorrent" },
			{ "st", "sharktorrent" }, { "SZ", "Shareaza" },
			{ "TN", "TorrentDotNET" }, { "TR", "Transmission" },
			{ "TS", "Torrentstorm" }, { "TT", "TuoTu" }, { "UL", "uLeecher!" },
			{ "UM", "µTorrent for Mac" }, { "UT", "µTorrent" },
			{ "VG", "Vagaa" }, { "WT", "BitLet" }, { "WY", "FireTorrent" },
			{ "XL", "Xunlei" }, { "XS", "XSwifter" }, { "XT", "XanTorrent" },
			{ "XX", "Xtorrent" }, { "ZT", "ZipTorrent" } };

	private final String hostId;

	private ByteBuffer peerId;
	private String hexPeerId;

	/**
	 * Instantiate a new peer.
	 *
	 * @param address The peer's address, with port.
	 */
	public Peer(InetSocketAddress address) {
		this(address, null);
	}

	/**
	 * Instantiate a new peer.
	 *
	 * @param ip The peer's IP address.
	 * @param port The peer's port.
	 */
	public Peer(String ip, int port) {
		this(new InetSocketAddress(ip, port), null);
	}

	/**
	 * Instantiate a new peer.
	 *
	 * @param ip The peer's IP address.
	 * @param port The peer's port.
	 * @param peerId The byte-encoded peer ID.
	 */
	public Peer(String ip, int port, ByteBuffer peerId) {
		this(new InetSocketAddress(ip, port), peerId);
	}

	/**
	 * Instantiate a new peer.
	 *
	 * @param address The peer's address, with port.
	 * @param peerId The byte-encoded peer ID.
	 */
	public Peer(InetSocketAddress address, ByteBuffer peerId) {
		this.address = address;
		this.hostId = String.format("%s:%d",
			this.address.getAddress(),
			this.address.getPort());

		this.setPeerId(peerId);
	}

	/**
	 * Tells whether this peer has a known peer ID yet or not.
	 */
	public boolean hasPeerId() {
		return this.peerId != null;
	}

	/**
	 * Returns the raw peer ID as a {@link ByteBuffer}.
	 */
	public ByteBuffer getPeerId() {
		return this.peerId;
	}

	/**
	 * Set a peer ID for this peer (usually during handshake).
	 *
	 * @param peerId The new peer ID for this peer.
	 */
	public void setPeerId(ByteBuffer peerId) {
		if (peerId != null) {
			this.peerId = peerId;
			this.hexPeerId = Torrent.byteArrayToHexString(peerId.array());
		} else {
			this.peerId = null;
			this.hexPeerId = null;
		}
	}

	public String getPeerName() {
		if (this.peerId != null) {
			String name = new String(this.peerId.array());

			for (String[] clientName : clientNameList) {
				if (name.startsWith("-" + clientName[0])) {
					return clientName[1] + " "
							+ name.substring(clientName[0].length() + 1, clientName[0].length() + 5);
				}
			}
		}

		return this.hexPeerId.substring(this.hexPeerId.length() - 6);
	}

	/**
	 * Get the hexadecimal-encoded string representation of this peer's ID.
	 */
	public String getHexPeerId() {
		return this.hexPeerId;
	}

	/**
	 * Get the shortened hexadecimal-encoded peer ID.
	 */
	public String getShortHexPeerId() {
		return String.format("..%s",
			this.hexPeerId.substring(this.hexPeerId.length()-6).toUpperCase());
	}

	/**
	 * Returns this peer's IP address.
	 */
	public String getIp() {
		return this.address.getAddress().getHostAddress();
	}

	/**
	 * Returns this peer's InetAddress.
	 */
	public InetAddress getAddress() {
		return this.address.getAddress();
	}

	/**
	 * Returns this peer's port number.
	 */
	public int getPort() {
		return this.address.getPort();
	}

	/**
	 * Returns this peer's host identifier ("host:port").
	 */
	public String getHostIdentifier() {
		return this.hostId;
	}

	/**
	 * Returns a binary representation of the peer's IP.
	 */
	public byte[] getRawIp() {
		return this.address.getAddress().getAddress();
	}

	/**
	 * Returns a human-readable representation of this peer.
	 */
	public String toString() {
		StringBuilder s = new StringBuilder("peer://")
			.append(this.getIp()).append(":").append(this.getPort())
			.append("/");

		if (this.hasPeerId()) {
			s.append(getPeerName());
		} else {
			s.append("?");
		}

		if (this.getPort() < 10000) {
			s.append(" ");
		}

		return s.toString();
	}

	/**
	 * Tells if two peers seem to look alike (i.e. they have the same IP, port
	 * and peer ID if they have one).
	 */
	public boolean looksLike(Peer other) {
		if (other == null) {
			return false;
		}

		return this.hostId.equals(other.hostId) &&
			(this.hasPeerId()
				 ? this.hexPeerId.equals(other.hexPeerId)
				 : true);
	}
}
