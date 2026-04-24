package org.kreotak.grott.registry;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of connected dataloggers and their inverters.
 * Also stores the last-seen command responses keyed by record-type → register-key → value map.
 */
@Component
public class DeviceRegistry {

    /** loggerId → DataloggerEntry */
    private final ConcurrentHashMap<String, DataloggerEntry> loggers = new ConcurrentHashMap<>();

    /** recordType → regKey(4 hex) → response map, e.g. "05" → "001f" → {"value":"..."} */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Map<String, String>>> commandResponses
            = new ConcurrentHashMap<>();

    // ── datalogger ────────────────────────────────────────────────────────────

    public DataloggerEntry getOrCreate(String loggerId, String ip, int port, String protocol) {
        return loggers.compute(loggerId, (k, existing) -> {
            if (existing == null) return new DataloggerEntry(loggerId, ip, port, protocol);
            existing.update(ip, port, protocol);
            return existing;
        });
    }

    public Optional<DataloggerEntry> find(String loggerId) {
        return Optional.ofNullable(loggers.get(loggerId));
    }

    public void remove(String ip, int port) {
        loggers.values().removeIf(e -> e.getIp().equals(ip) && e.getPort() == port);
    }

    public Collection<DataloggerEntry> allLoggers() {
        return loggers.values();
    }

    public Optional<DataloggerEntry> findLoggerForInverter(String inverterId) {
        return loggers.values().stream()
                .filter(l -> l.hasInverter(inverterId))
                .findFirst();
    }

    // ── command responses ─────────────────────────────────────────────────────

    public void putCommandResponse(String recType, String regKey, Map<String, String> response) {
        commandResponses.computeIfAbsent(recType, k -> new ConcurrentHashMap<>())
                        .put(regKey, response);
    }

    public Optional<Map<String, String>> getCommandResponse(String recType, String regKey) {
        ConcurrentHashMap<String, Map<String, String>> byType = commandResponses.get(recType);
        if (byType == null) return Optional.empty();
        return Optional.ofNullable(byType.get(regKey));
    }

    public Map<String, Map<String, String>> getAllCommandResponses(String recType) {
        return commandResponses.getOrDefault(recType, new ConcurrentHashMap<>());
    }

    public void removeCommandResponse(String recType, String regKey) {
        ConcurrentHashMap<String, Map<String, String>> byType = commandResponses.get(recType);
        if (byType != null) byType.remove(regKey);
    }
}
