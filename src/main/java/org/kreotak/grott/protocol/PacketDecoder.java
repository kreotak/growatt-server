package org.kreotak.grott.protocol;

import java.nio.charset.StandardCharsets;

public final class PacketDecoder {

    private static final byte[] XOR_KEY = "Growatt".getBytes(StandardCharsets.US_ASCII);

    private PacketDecoder() {}

    /**
     * Decodes raw bytes received from an inverter/datalogger into a {@link GrowattPacket}.
     * For protocol 05/06 the body is XOR-decrypted; the header (first 8 bytes) is never encrypted.
     * The trailing CRC (2 bytes, protocol 05/06) is stripped from hexData.
     */
    public static GrowattPacket decode(byte[] raw) {
        if (raw.length < 8) throw new IllegalArgumentException("Packet too short: " + raw.length);

        String hexRaw = toHex(raw);
        String protocol  = hexRaw.substring(6, 8);
        int sequenceNo   = Integer.parseInt(hexRaw.substring(0, 4), 16);
        int payloadLen   = Integer.parseInt(hexRaw.substring(8, 12), 16);
        String deviceId  = hexRaw.substring(12, 14);
        String recType   = hexRaw.substring(14, 16);

        String hexDecrypted;
        if ("05".equals(protocol) || "06".equals(protocol)) {
            // strip trailing 2-byte CRC before storing
            byte[] stripped = stripCrc(raw);
            hexDecrypted = decrypt(stripped);
        } else {
            hexDecrypted = hexRaw;
        }

        return new GrowattPacket(sequenceNo, protocol, payloadLen, deviceId, recType, hexDecrypted);
    }

    /** XOR-decrypts bytes 8+ with the cycling "Growatt" key; returns full hex string. */
    public static String decrypt(byte[] data) {
        byte[] out = new byte[data.length];
        System.arraycopy(data, 0, out, 0, Math.min(8, data.length));
        int kLen = XOR_KEY.length;
        for (int i = 8; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ XOR_KEY[(i - 8) % kLen]);
        }
        return toHex(out);
    }

    /** Encrypts bytes 8+ (same operation as decrypt – XOR is symmetric). */
    public static byte[] encrypt(byte[] plain) {
        return fromHex(decrypt(plain));
    }

    public static boolean validateCrc(byte[] raw, String protocol) {
        if (!"05".equals(protocol) && !"06".equals(protocol)) return true;
        return Crc16Modbus.verify(raw);
    }

    // ── hex helpers ──────────────────────────────────────────────────────────

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }

    public static byte[] fromHex(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(hex, i, i + 2, 16);
        }
        return out;
    }

    private static byte[] stripCrc(byte[] raw) {
        if (raw.length < 2) return raw;
        byte[] out = new byte[raw.length - 2];
        System.arraycopy(raw, 0, out, 0, out.length);
        return out;
    }
}
