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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class UdpServer {

    private static final Logger log = LoggerFactory.getLogger(UdpServer.class);
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    private final GrottProperties props;
    private final DeviceRegistry registry;
    private final RecordParser parser;
    private final List<OutputService> outputs;

    private DatagramSocket socket;
    private volatile boolean running;

    final ConcurrentHashMap<String, InetSocketAddress> clients = new ConcurrentHashMap<>();

    public UdpServer(GrottProperties props, DeviceRegistry registry,
                     RecordParser parser, List<OutputService> outputs) {
        this.props    = props;
        this.registry = registry;
        this.parser   = parser;
        this.outputs  = outputs;
    }

    @PostConstruct
    public void start() throws SocketException {
        socket = new DatagramSocket(props.getServerPort());
        running = true;
        log.info("UDP server listening on {}:{}", props.getServerHost(), props.getServerPort());
        Thread.ofVirtual().name("grott-udp-receiver").start(this::receiveLoop);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (socket != null) socket.close();
        log.info("UDP server stopped");
    }

    public int getActiveSessionCount() {
        return clients.size();
    }

    public boolean sendCommand(String ip, int port, byte[] command) {
        InetSocketAddress addr = clients.get(ip + "_" + port);
        if (addr == null) return false;
        send(command, addr);
        return true;
    }

    // ── receive loop ──────────────────────────────────────────────────────────

    private void receiveLoop() {
        byte[] buf = new byte[4096];
        while (running) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);
                byte[] data = Arrays.copyOf(pkt.getData(), pkt.getLength());
                InetSocketAddress sender = new InetSocketAddress(pkt.getAddress(), pkt.getPort());
                String ip  = pkt.getAddress().getHostAddress();
                String key = ip + "_" + pkt.getPort();
                if (clients.put(key, sender) == null) {
                    log.info("UDP new client {}:{}", ip, pkt.getPort());
                    // Remove stale entries for the same IP on a different port (reconnect)
                    clients.keySet().removeIf(k -> k.startsWith(ip + "_") && !k.equals(key));
                }
                Thread.ofVirtual().name("grott-udp-" + key).start(() -> handleDatagram(data, sender));
            } catch (IOException e) {
                if (running) log.error("UDP receive error: {}", e.getMessage());
            }
        }
    }

    // ── packet dispatch ───────────────────────────────────────────────────────

    private void handleDatagram(byte[] raw, InetSocketAddress sender) {
        String clientIp   = sender.getAddress().getHostAddress();
        int    clientPort = sender.getPort();
        try {
            if (props.isVerbose()) log.info("UDP {} bytes from {}:{}", raw.length, clientIp, clientPort);

            GrowattPacket pkt = PacketDecoder.decode(raw);
            String recType = pkt.recordType();

            if ("16".equals(recType)) {
                log.debug("UDP ping from {}", clientIp);
                registry.getOrCreate(extractLoggerId(pkt), clientIp, clientPort, pkt.protocol());
                send(raw, sender);
            } else if ("03".equals(recType)) {
                handleRegistration(pkt, sender);
            } else if (recType.matches("04|50|1b|20")) {
                send(PacketEncoder.buildAck(pkt), sender);
                Optional<InverterData> result = parser.parse(pkt, raw);
                result.ifPresent(data -> {
                    if (props.isVerbose()) log.info("UDP parsed device={} layout={}", data.deviceId(), data.layoutName());
                    for (OutputService out : outputs) {
                        try { out.publish(data); } catch (Exception e) {
                            log.warn("Output service {} failed: {}", out.getClass().getSimpleName(), e.getMessage());
                        }
                    }
                });
            } else if (recType.matches("05|06|18|19")) {
                handleCommandResponse(pkt);
            } else if ("10".equals(recType)) {
                handleMultiregResponse(pkt);
            } else if ("29".equals(recType)) {
                // no response needed
            } else {
                log.debug("UDP unknown record type {} from {}", recType, clientIp);
            }
        } catch (Exception e) {
            log.error("Error processing UDP packet from {}:{}: {}", clientIp, clientPort, e.getMessage(), e);
        }
    }

    private void handleRegistration(GrowattPacket pkt, InetSocketAddress sender) {
        String hex      = pkt.hexData();
        String loggerId = decodeAscii(hex, 16, 10);
        String inverterId;
        if ("02".equals(pkt.protocol()) || "05".equals(pkt.protocol())) {
            inverterId = decodeAscii(hex, 36, 10);
        } else {
            inverterId = decodeAscii(hex, 76, 10);
        }
        String clientIp   = sender.getAddress().getHostAddress();
        int    clientPort = sender.getPort();
        log.info("UDP registration: logger={} inverter={} proto={}", loggerId, inverterId, pkt.protocol());

        DataloggerEntry entry = registry.getOrCreate(loggerId, clientIp, clientPort, pkt.protocol());
        entry.addInverter(new InverterEntry(inverterId, pkt.deviceId()));

        send(PacketEncoder.buildAck(pkt), sender);
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        send(PacketEncoder.buildTimeCommand(pkt.protocol(), loggerId, SEQ.getAndIncrement()), sender);
        log.info("UDP registration complete for logger={}", loggerId);
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
        String hex       = pkt.hexData();
        int startReg     = Integer.parseInt(hex.substring(76, 80), 16);
        int endReg       = Integer.parseInt(hex.substring(80, 84), 16);
        String value     = hex.substring(84, 86);
        String regKey    = String.format("%04x%04x", startReg, endReg);
        registry.putCommandResponse("10", regKey, Map.of("value", value));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void send(byte[] data, InetSocketAddress addr) {
        try {
            socket.send(new DatagramPacket(data, data.length, addr));
            if (props.isVerbose()) log.info("UDP sent {} bytes to {}", data.length, addr);
        } catch (IOException e) {
            log.warn("UDP send failed to {}: {}", addr, e.getMessage());
        }
    }

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
}
