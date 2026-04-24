package org.kreotak.grott.protocol;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.kreotak.grott.protocol.PacketDecoder.fromHex;
import static org.kreotak.grott.protocol.PacketDecoder.toHex;

/**
 * Builds outbound Growatt protocol packets.
 *
 * All methods produce a ready-to-send byte array (encrypted + CRC appended for 05/06).
 */
public final class PacketEncoder {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private PacketEncoder() {}

    /**
     * ACK packet for data records (types 03, 04, 50, 1b, 20, 16).
     * Payload = deviceId(1) + recType(1) + status(1) = length 3.
     */
    public static byte[] buildAck(GrowattPacket pkt) {
        // header: seq(2) + 00 + proto(1) = first 8 hex chars (4 bytes)
        String hdr = pkt.headerHex().substring(0, 8)   // seq + 00 + proto
                   + "0003"                              // payload length = 3
                   + pkt.deviceId() + pkt.recordType(); // device + rectype (2 bytes)

        if (pkt.isEncrypted()) {
            // status 0x00 XOR 'G' (0x47) = 0x47
            String plain = hdr + "47";
            byte[] plainBytes = fromHex(plain);
            int crc = Crc16Modbus.calculate(plainBytes);
            return concat(plainBytes, new byte[]{(byte)(crc >> 8), (byte)(crc & 0xFF)});
        } else {
            return fromHex(hdr + "00");
        }
    }

    /**
     * Time-synchronisation command sent right after a type-03 registration.
     * Record type 0x18, register 31 (0x001f).
     */
    public static byte[] buildTimeCommand(String protocol, String loggerId, int seqNo) {
        String time = LocalDateTime.now().format(DT_FMT);
        String timeHex = toHex(time.getBytes(StandardCharsets.UTF_8));
        String timeLen = String.format("%04x", timeHex.length() / 2);

        String body = toHex(loggerId.getBytes(StandardCharsets.UTF_8));
        if ("06".equals(protocol)) body += "0000000000000000000000000000000000000000"; // 20 zero bytes
        body += "001f";       // register 31
        body += timeLen + timeHex;

        int payloadLen = body.length() / 2 + 2; // body_bytes + deviceId(1) + recType(1)
        String header = String.format("%04x", seqNo) + "00" + protocol
                      + String.format("%04x", payloadLen) + "0118"; // device 01, type 18

        return finalise(protocol, header + body);
    }

    /**
     * Read-register command (type 05 for inverter, type 19 for datalogger).
     */
    public static byte[] buildReadRegister(String protocol, String loggerId, String deviceId,
                                           String cmdType, int register, int seqNo) {
        String body = toHex(loggerId.getBytes(StandardCharsets.UTF_8));
        if ("06".equals(protocol)) body += "0000000000000000000000000000000000000000";
        body += String.format("%04x", register);
        body += String.format("%04x", register); // start == end for single read

        int payloadLen = body.length() / 2 + 2;
        String header = String.format("%04x", seqNo) + "00" + protocol
                      + String.format("%04x", payloadLen) + deviceId + cmdType;

        return finalise(protocol, header + body);
    }

    /**
     * Write-register command (type 06 for inverter, type 18 for datalogger).
     * {@code value} is the raw 16-bit register value.
     */
    public static byte[] buildWriteRegister(String protocol, String loggerId, String deviceId,
                                            String cmdType, int register, int value, int seqNo) {
        String body = toHex(loggerId.getBytes(StandardCharsets.UTF_8));
        if ("06".equals(protocol)) body += "0000000000000000000000000000000000000000";
        body += String.format("%04x", register);
        body += String.format("%04x", value);

        int payloadLen = body.length() / 2 + 2;
        String header = String.format("%04x", seqNo) + "00" + protocol
                      + String.format("%04x", payloadLen) + deviceId + cmdType;

        return finalise(protocol, header + body);
    }

    /**
     * Write string value to a register (type 18 datalogger, e.g. datetime).
     */
    public static byte[] buildWriteRegisterText(String protocol, String loggerId, String deviceId,
                                                String cmdType, int register, String textValue,
                                                int seqNo) {
        String valHex = toHex(textValue.getBytes(StandardCharsets.UTF_8));
        String valLen = String.format("%04x", valHex.length() / 2);

        String body = toHex(loggerId.getBytes(StandardCharsets.UTF_8));
        if ("06".equals(protocol)) body += "0000000000000000000000000000000000000000";
        body += String.format("%04x", register) + valLen + valHex;

        int payloadLen = body.length() / 2 + 2;
        String header = String.format("%04x", seqNo) + "00" + protocol
                      + String.format("%04x", payloadLen) + deviceId + cmdType;

        return finalise(protocol, header + body);
    }

    // ── internals ────────────────────────────────────────────────────────────

    private static byte[] finalise(String protocol, String hexPlain) {
        byte[] plain = fromHex(hexPlain);
        if (!"02".equals(protocol)) {
            byte[] encrypted = PacketDecoder.encrypt(plain);
            int crc = Crc16Modbus.calculate(encrypted);
            return concat(encrypted, new byte[]{(byte)(crc >> 8), (byte)(crc & 0xFF)});
        }
        return plain;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
