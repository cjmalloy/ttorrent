/** Copyright (C) 2011 Turn, Inc.
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

import java.nio.ByteBuffer;

/**
 * A basic BitTorrent peer.
 * 
 * This class is meant to be a common base for the tracker and client, which
 * would presumably subclass it to extend its functionnality and fields.
 * 
 * @author mpetazzoni
 */
public class Peer {

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

	private final String ip;
	private final int port;
	private final String hostId;

	private ByteBuffer peerId;
	private String hexPeerId;

	/**
	 * Instanciate a new peer for the given torrent.
	 * 
	 * @param ip
	 *            The peer's IP address.
	 * @param port
	 *            The peer's port.
	 * @param peerId
	 *            The byte-encoded peer ID.
	 */
	public Peer(String ip, int port, ByteBuffer peerId) {
		this.ip = ip;
		this.port = port;
		this.hostId = String.format("%s:%d", ip, port);

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
	 * @param peerId
	 *            The new peer ID for this peer.
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
	 * Returns this peer's IP address.
	 */
	public String getIp() {
		return this.ip;
	}

	/**
	 * Returns this peer's port number.
	 */
	public int getPort() {
		return this.port;
	}

	/**
	 * Returns this peer's host identifier ("host:port").
	 */
	public String getHostIdentifier() {
		return this.hostId;
	}

	/**
	 * Returns a human-readable representation of this peer.
	 */
	public String toString() {
		StringBuilder s = new StringBuilder("peer://").append(this.ip)
				.append(":").append(this.port).append("/");

		if (this.hasPeerId()) {
			// s.append(this.hexPeerId.substring(this.hexPeerId.length() - 6));
			s.append(getPeerName());
		} else {
			s.append("?");
		}

		return s.toString();
	}

	/**
	 * Tells if two peers seem to lookalike (i.e. they have the same IP, port
	 * and peer ID if they have one).
	 */
	public boolean looksLike(Peer other) {
		if (other == null) {
			return false;
		}

		return this.hostId.equals(other.hostId)
				&& (this.hasPeerId() ? this.hexPeerId.equals(other.hexPeerId)
						: true);
	}
}
