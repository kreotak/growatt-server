package org.kreotak.grott.protocol;

/**
 * Decoded Growatt protocol packet.
 *
 * All field offsets in the layout JSON files are positions in the hex-encoded
 * decrypted packet string (1 byte = 2 hex chars), matching the original Python
 * implementation exactly.
 *
 * Packet wire format (8-byte header, always plain-text):
 *   [0-1] sequence number  (uint16 BE)
 *   [2]   0x00
 *   [3]   protocol         (0x02 | 0x05 | 0x06)
 *   [4-5] payload length   (uint16 BE, counts bytes from offset 6 to end, CRC excluded)
 *   [6]   device id
 *   [7]   record type
 *   [8..] body             (XOR-encrypted for protocol 05/06)
 *   last 2 bytes: CRC-16 Modbus (protocol 05/06 only, not in payload length)
 */
public record GrowattPacket(
        int sequenceNo,
        String protocol,    // "02" | "05" | "06"
        int payloadLength,
        String deviceId,    // 2 hex chars, e.g. "01"
        String recordType,  // 2 hex chars, e.g. "04"
        String hexData      // full decrypted hex string of the entire packet (no trailing CRC)
) {
    public boolean isEncrypted() {
        return "05".equals(protocol) || "06".equals(protocol);
    }

    /** First 8 bytes (16 hex chars) – always unencrypted. */
    public String headerHex() {
        return hexData.substring(0, 16);
    }
}
