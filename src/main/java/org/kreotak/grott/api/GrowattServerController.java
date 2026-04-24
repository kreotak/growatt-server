package org.kreotak.grott.api;

import org.kreotak.grott.config.GrottProperties;
import org.kreotak.grott.protocol.PacketEncoder;
import org.kreotak.grott.registry.DataloggerEntry;
import org.kreotak.grott.registry.DeviceRegistry;
import org.kreotak.grott.registry.InverterEntry;
import org.kreotak.grott.server.TcpServer;
import org.kreotak.grott.server.UdpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP API mirroring grottserver.py's HTTP endpoints.
 *
 * GET  /datalogger?command=register&datalogger=X&register=Y        – read register
 * GET  /datalogger?command=regall&datalogger=X                     – all cached register values
 * GET  /inverter?command=register&inverter=X&register=Y&format=dec – read inverter register
 * GET  /inverter?command=regall                                     – all cached inverter register values
 * PUT  /datalogger?command=register&datalogger=X&register=Y&value=Z – write register
 * PUT  /datalogger?command=datetime&datalogger=X                   – sync datalogger time
 * PUT  /inverter?command=register&inverter=X&register=Y&value=Z    – write inverter register
 * GET  /info                                                        – server status
 */
@RestController
public class GrowattServerController {

    private static final Logger log = LoggerFactory.getLogger(GrowattServerController.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    private final DeviceRegistry registry;
    private final TcpServer tcpServer;
    private final UdpServer udpServer;
    private final GrottProperties props;

    public GrowattServerController(DeviceRegistry registry, TcpServer tcpServer, UdpServer udpServer, GrottProperties props) {
        this.registry  = registry;
        this.tcpServer = tcpServer;
        this.udpServer = udpServer;
        this.props     = props;
    }

    // ── info ──────────────────────────────────────────────────────────────────

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> loggers = new ArrayList<>();
        for (DataloggerEntry e : registry.allLoggers()) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("id", e.getLoggerId());
            l.put("ip", e.getIp());
            l.put("port", e.getPort());
            l.put("protocol", e.getProtocol());
            List<String> invs = e.getInverters().stream().map(InverterEntry::getInverterId).toList();
            l.put("inverters", invs);
            loggers.add(l);
        }
        out.put("loggers", loggers);
        out.put("activeTcpConnections", tcpServer.getActiveConnectionCount());
        out.put("activeUdpSessions", udpServer.getActiveSessionCount());
        return ResponseEntity.ok(out);
    }

    // ── datalogger GET ────────────────────────────────────────────────────────

    @GetMapping("/datalogger")
    public ResponseEntity<?> dataloggerGet(
            @RequestParam(required = false) String command,
            @RequestParam(required = false) String datalogger,
            @RequestParam(required = false) Integer register) {

        if (command == null) {
            return buildLoggerList();
        }
        if ("regall".equals(command)) {
            return ResponseEntity.ok(registry.getAllCommandResponses("19"));
        }
        if (!"register".equals(command)) return badRequest("unknown command");
        if (datalogger == null || register == null) return badRequest("datalogger and register required");

        Optional<DataloggerEntry> loggerOpt = registry.find(datalogger);
        if (loggerOpt.isEmpty()) return badRequest("unknown datalogger");
        DataloggerEntry logger = loggerOpt.get();

        String regKey = String.format("%04x", register);
        registry.removeCommandResponse("19", regKey);

        byte[] cmd = PacketEncoder.buildReadRegister(logger.getProtocol(), datalogger,
                "01", "19", register, SEQ.getAndIncrement());

        if (!tcpServer.enqueueCommand(logger.getIp(), logger.getPort(), cmd)
                && !udpServer.sendCommand(logger.getIp(), logger.getPort(), cmd))
            return badRequest("datalogger not connected");

        return waitForResponse("19", regKey, props.getDataloggerResponseWaitMs());
    }

    // ── datalogger PUT ────────────────────────────────────────────────────────

    @PutMapping("/datalogger")
    public ResponseEntity<?> dataloggerPut(
            @RequestParam(required = false) String command,
            @RequestParam(required = false) String datalogger,
            @RequestParam(required = false) Integer register,
            @RequestParam(required = false) String value) {

        if (command == null) return badRequest("command required");

        Optional<DataloggerEntry> loggerOpt = Optional.ofNullable(datalogger).flatMap(registry::find);
        if (loggerOpt.isEmpty()) return badRequest("unknown datalogger");
        DataloggerEntry logger = loggerOpt.get();

        if ("datetime".equals(command)) {
            String now = LocalDateTime.now().format(DT_FMT);
            byte[] cmd = PacketEncoder.buildWriteRegisterText(logger.getProtocol(), datalogger,
                    "01", "18", 31, now, SEQ.getAndIncrement());
            if (!tcpServer.enqueueCommand(logger.getIp(), logger.getPort(), cmd))
                return badRequest("datalogger not connected");
            String regKey = String.format("%04x", 31);
            return waitForResponse("18", regKey, props.getDataloggerResponseWaitMs());
        }

        if ("register".equals(command)) {
            if (register == null || value == null) return badRequest("register and value required");
            String regKey = String.format("%04x", register);
            registry.removeCommandResponse("18", regKey);
            byte[] cmd = PacketEncoder.buildWriteRegisterText(logger.getProtocol(), datalogger,
                    "01", "18", register, value, SEQ.getAndIncrement());
            if (!tcpServer.enqueueCommand(logger.getIp(), logger.getPort(), cmd))
                return badRequest("datalogger not connected");
            return waitForResponse("18", regKey, props.getDataloggerResponseWaitMs());
        }

        return badRequest("unknown command");
    }

    // ── inverter GET ──────────────────────────────────────────────────────────

    @GetMapping("/inverter")
    public ResponseEntity<?> inverterGet(
            @RequestParam(required = false) String command,
            @RequestParam(required = false) String inverter,
            @RequestParam(defaultValue = "dec") String format,
            @RequestParam(required = false) Integer register) {

        if (command == null) return buildLoggerList();
        if ("regall".equals(command)) return ResponseEntity.ok(registry.getAllCommandResponses("05"));
        if (!"register".equals(command)) return badRequest("unknown command");
        if (inverter == null || register == null) return badRequest("inverter and register required");

        Optional<DataloggerEntry> loggerOpt = registry.findLoggerForInverter(inverter);
        if (loggerOpt.isEmpty()) return badRequest("unknown inverter");
        DataloggerEntry logger = loggerOpt.get();
        InverterEntry inv = logger.getInverter(inverter);

        String regKey = String.format("%04x", register);
        registry.removeCommandResponse("05", regKey);

        byte[] cmd = PacketEncoder.buildReadRegister(logger.getProtocol(), logger.getLoggerId(),
                inv.getInverterNo(), "05", register, SEQ.getAndIncrement());
        if (!tcpServer.enqueueCommand(logger.getIp(), logger.getPort(), cmd)
                && !udpServer.sendCommand(logger.getIp(), logger.getPort(), cmd))
            return badRequest("inverter not connected");

        ResponseEntity<?> resp = waitForResponse("05", regKey, props.getInverterResponseWaitMs());
        if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() instanceof Map<?,?> m) {
            String rawVal = String.valueOf(m.get("value"));
            Object converted = switch (format) {
                case "text" -> hexToText(rawVal);
                case "hex"  -> rawVal;
                default     -> Long.parseLong(rawVal, 16);
            };
            return ResponseEntity.ok(Map.of("register", register, "value", converted));
        }
        return resp;
    }

    // ── inverter PUT ──────────────────────────────────────────────────────────

    @PutMapping("/inverter")
    public ResponseEntity<?> inverterPut(
            @RequestParam(required = false) String command,
            @RequestParam(required = false) String inverter,
            @RequestParam(required = false) Integer register,
            @RequestParam(required = false) String value,
            @RequestParam(defaultValue = "dec") String format) {

        if (!"register".equals(command)) return badRequest("unknown command");
        if (inverter == null || register == null || value == null) return badRequest("inverter, register and value required");

        Optional<DataloggerEntry> loggerOpt = registry.findLoggerForInverter(inverter);
        if (loggerOpt.isEmpty()) return badRequest("unknown inverter");
        DataloggerEntry logger = loggerOpt.get();
        InverterEntry inv = logger.getInverter(inverter);

        int intValue = switch (format) {
            case "hex"  -> Integer.parseInt(value, 16);
            case "text" -> Integer.parseInt(value.chars()
                    .mapToObj(Integer::toString).reduce("", (a, b) -> a + b));
            default     -> Integer.parseInt(value);
        };

        String regKey = String.format("%04x", register);
        registry.removeCommandResponse("06", regKey);

        byte[] cmd = PacketEncoder.buildWriteRegister(logger.getProtocol(), logger.getLoggerId(),
                inv.getInverterNo(), "06", register, intValue, SEQ.getAndIncrement());
        if (!tcpServer.enqueueCommand(logger.getIp(), logger.getPort(), cmd)
                && !udpServer.sendCommand(logger.getIp(), logger.getPort(), cmd))
            return badRequest("inverter not connected");

        return waitForResponse("06", regKey, props.getInverterResponseWaitMs());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<?> waitForResponse(String recType, String regKey, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Optional<Map<String, String>> resp = registry.getCommandResponse(recType, regKey);
            if (resp.isPresent()) return ResponseEntity.ok(resp.get());
            try { Thread.sleep(500); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return ResponseEntity.status(408).body(Map.of("error", "timeout waiting for response"));
    }

    private ResponseEntity<?> buildLoggerList() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (DataloggerEntry e : registry.allLoggers()) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("ip", e.getIp());
            l.put("port", e.getPort());
            l.put("protocol", e.getProtocol());
            for (InverterEntry inv : e.getInverters()) {
                l.put(inv.getInverterId(), Map.of("inverterno", inv.getInverterNo()));
            }
            out.put(e.getLoggerId(), l);
        }
        return ResponseEntity.ok(out);
    }

    private ResponseEntity<Map<String, String>> badRequest(String msg) {
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    private String hexToText(String hex) {
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++)
            b[i] = (byte) Integer.parseInt(hex, i * 2, i * 2 + 2, 16);
        return new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }
}
