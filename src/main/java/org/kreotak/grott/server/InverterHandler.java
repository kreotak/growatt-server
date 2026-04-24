package org.kreotak.grott.server;

import org.kreotak.grott.config.GrottProperties;
import org.kreotak.grott.output.OutputService;
import org.kreotak.grott.parser.InverterData;
import org.kreotak.grott.parser.RecordParser;
import org.kreotak.grott.protocol.GrowattPacket;
import org.kreotak.grott.protocol.PacketDecoder;
import org.kreotak.grott.protocol.PacketEncoder;
import org.kreotak.grott.registry.DataloggerEntry;
import org.kreotak.grott.registry.DeviceRegistry;
import org.kreotak.grott.registry.InverterEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles one persistent inverter/datalogger TCP connection.
 * Spawns two virtual threads per connection: reader and writer.
 */
public class InverterHandler {

    private static final Logger log = LoggerFactory.getLogger(InverterHandler.class);
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    private final Socket socket;
    private final String clientIp;
    private final int clientPort;
    private final GrottProperties props;
    private final DeviceRegistry registry;
    private final RecordParser parser;
    private final List<OutputService> outputs;

    final LinkedBlockingQueue<byte[]> sendQueue = new LinkedBlockingQueue<>(256);
    private Runnable onClose;

    public InverterHandler(Socket socket, GrottProperties props, DeviceRegistry registry,
                           RecordParser parser, List<OutputService> outputs) throws IOException {
        this.socket     = socket;
        this.clientIp   = socket.getInetAddress().getHostAddress();
        this.clientPort = socket.getPort();
        this.props      = props;
        this.registry   = registry;
        this.parser     = parser;
        this.outputs    = outputs;
    }

    public void setOnClose(Runnable onClose) { this.onClose = onClose; }

    public void start() {
        Thread.ofVirtual().name("grott-writer-" + clientIp).start(this::writerLoop);
        Thread.ofVirtual().name("grott-reader-" + clientIp).start(this::readerLoop);
    }

    // ── reader ────────────────────────────────────────────────────────────────

    private void readerLoop() {
        log.info("Connection established from {}:{}", clientIp, clientPort);
        try (InputStream in = socket.getInputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                byte[] raw = new byte[n];
                System.arraycopy(buf, 0, raw, 0, n);
                handlePacket(raw);
            }
        } catch (IOException e) {
            log.debug("Connection closed for {}:{}: {}", clientIp, clientPort, e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handlePacket(byte[] raw) {
        try {
            if (props.isVerbose()) log.info("Received {} bytes from {}:{}", raw.length, clientIp, clientPort);

            GrowattPacket pkt = PacketDecoder.decode(raw);
            String recType = pkt.recordType();

            if ("16".equals(recType)) {
                handlePing(pkt, raw);
            } else if ("03".equals(recType)) {
                handleRegistration(pkt, raw);
            } else if (recType.matches("04|50|1b|20")) {
                handleDataRecord(pkt, raw);
            } else if (recType.matches("05|06|18|19")) {
                handleCommandResponse(pkt);
            } else if ("10".equals(recType)) {
                handleMultiregResponse(pkt);
            } else if ("29".equals(recType)) {
                // no response needed
            } else {
                log.debug("Unknown record type {} from {}", recType, clientIp);
            }
        } catch (Exception e) {
            log.error("Error processing packet from {}: {}", clientIp, e.getMessage(), e);
        }
    }

    // ── record handlers ───────────────────────────────────────────────────────

    private void handlePing(GrowattPacket pkt, byte[] raw) {
        log.debug("Ping from {}", clientIp);
        String loggerId = extractLoggerId(pkt);
        registry.getOrCreate(loggerId, clientIp, clientPort, pkt.protocol());
        sendQueue.add(raw); // echo back
    }

    private void handleRegistration(GrowattPacket pkt, byte[] raw) {
        String hex = pkt.hexData();
        String loggerId  = decodeAscii(hex, 16, 10);
        String inverterId;
        if ("02".equals(pkt.protocol()) || "05".equals(pkt.protocol())) {
            inverterId = decodeAscii(hex, 36, 10);
        } else {
            inverterId = decodeAscii(hex, 76, 10);
        }
        log.info("Registration: logger={} inverter={} proto={}", loggerId, inverterId, pkt.protocol());

        DataloggerEntry entry = registry.getOrCreate(loggerId, clientIp, clientPort, pkt.protocol());
        entry.addInverter(new InverterEntry(inverterId, pkt.deviceId()));

        // ACK first
        sendQueue.add(PacketEncoder.buildAck(pkt));

        // Time-sync command
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        sendQueue.add(PacketEncoder.buildTimeCommand(pkt.protocol(), loggerId, SEQ.getAndIncrement()));
        log.info("Registration processed, time command queued for logger={}", loggerId);
    }

    private void handleDataRecord(GrowattPacket pkt, byte[] raw) {
        log.debug("Data record type={} from {}", pkt.recordType(), clientIp);
        sendQueue.add(PacketEncoder.buildAck(pkt));

        Optional<InverterData> result = parser.parse(pkt, raw);
        result.ifPresent(data -> {
            if (props.isVerbose()) log.info("Parsed data device={} layout={}", data.deviceId(), data.layoutName());
            for (OutputService out : outputs) {
                try { out.publish(data); } catch (Exception e) {
                    log.warn("Output service {} failed: {}", out.getClass().getSimpleName(), e.getMessage());
                }
            }
        });
    }

    private void handleCommandResponse(GrowattPacket pkt) {
        String hex     = pkt.hexData();
        String recType = pkt.recordType();
        int offset     = "06".equals(pkt.protocol()) ? 40 : 0;

        int register = Integer.parseInt(hex.substring(36 + offset, 40 + offset), 16);
        String regKey = String.format("%04x", register);

        Map<String, String> response = new HashMap<>();
        switch (recType) {
            case "05" -> {
                if (hex.length() >= 48 + offset) {
                    response.put("value", hex.substring(44 + offset, 48 + offset));
                    registry.putCommandResponse("05", regKey, response);
                }
            }
            case "06" -> {
                response.put("result", hex.substring(40 + offset, 42 + offset));
                response.put("value",  hex.substring(42 + offset, 46 + offset));
                registry.putCommandResponse("06", regKey, response);
                registry.putCommandResponse("05", regKey, Map.of("value", response.get("value")));
            }
            case "18" -> {
                response.put("result", hex.substring(40 + offset, 42 + offset));
                registry.putCommandResponse("18", regKey, response);
            }
            case "19" -> {
                int valLen = Integer.parseInt(hex.substring(40 + offset, 44 + offset), 16);
                String val = decodeHexToString(hex.substring(44 + offset, 44 + offset + valLen * 2));
                response.put("value", val);
                registry.putCommandResponse("19", regKey, response);
            }
        }
    }

    private void handleMultiregResponse(GrowattPacket pkt) {
        String hex = pkt.hexData();
        int startReg = Integer.parseInt(hex.substring(76, 80), 16);
        int endReg   = Integer.parseInt(hex.substring(80, 84), 16);
        String value = hex.substring(84, 86);
        String regKey = String.format("%04x%04x", startReg, endReg);
        registry.putCommandResponse("10", regKey, Map.of("value", value));
    }

    // ── writer ────────────────────────────────────────────────────────────────

    private void writerLoop() {
        try (OutputStream out = socket.getOutputStream()) {
            while (!socket.isClosed()) {
                byte[] msg = sendQueue.poll(5, TimeUnit.SECONDS);
                if (msg != null) {
                    out.write(msg);
                    out.flush();
                    if (props.isVerbose()) log.info("Sent {} bytes to {}:{}", msg.length, clientIp, clientPort);
                }
            }
        } catch (IOException e) {
            log.debug("Writer closed for {}:{}", clientIp, clientPort);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── cleanup ───────────────────────────────────────────────────────────────

    private void cleanup() {
        registry.remove(clientIp, clientPort);
        try { socket.close(); } catch (IOException ignored) {}
        log.info("Connection cleaned up for {}:{}", clientIp, clientPort);
        if (onClose != null) onClose.run();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String extractLoggerId(GrowattPacket pkt) {
        return decodeAscii(pkt.hexData(), 16, 10);
    }

    private String decodeAscii(String hex, int hexOffset, int byteLen) {
        String slice = hex.substring(hexOffset, hexOffset + byteLen * 2);
        byte[] bytes = new byte[byteLen];
        for (int i = 0; i < byteLen; i++)
            bytes[i] = (byte) Integer.parseInt(slice, i * 2, i * 2 + 2, 16);
        return new String(bytes, StandardCharsets.UTF_8).trim();
    }

    private String decodeHexToString(String hex) {
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++)
            b[i] = (byte) Integer.parseInt(hex, i * 2, i * 2 + 2, 16);
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    public String getClientKey() { return clientIp + "_" + clientPort; }
}
