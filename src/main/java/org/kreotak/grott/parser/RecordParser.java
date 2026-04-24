package org.kreotak.grott.parser;

import org.kreotak.grott.config.GrottProperties;
import org.kreotak.grott.protocol.GrowattPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Parses the body of a data packet into an {@link InverterData} using the
 * matching layout from {@link LayoutLoader}.
 *
 * Layout selection mirrors the Python implementation:
 *   layoutName = "T" + protocol + deviceId + recordType [+ "X" if extended] [+ invType]
 * Falls back to "T" + protocol + "NNNN" if no exact match.
 */
@Component
public class RecordParser {

    private static final Logger log = LoggerFactory.getLogger(RecordParser.class);
    private static final int EXTENDED_THRESHOLD = 375;
    private static final int SPH_THRESHOLD = 460;
    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final LayoutLoader layoutLoader;
    private final GrottProperties props;

    public RecordParser(LayoutLoader layoutLoader, GrottProperties props) {
        this.layoutLoader = layoutLoader;
        this.props = props;
    }

    /**
     * Returns empty if the packet is not a recognised data record or is too short.
     */
    public Optional<InverterData> parse(GrowattPacket pkt, byte[] rawBytes) {
        String hexData = pkt.hexData();
        int totalBytes = hexData.length() / 2;
        if (totalBytes < 12) return Optional.empty();

        boolean isSmartMeter = "20".equals(pkt.recordType()) || "1b".equals(pkt.recordType());
        boolean extended = (totalBytes > EXTENDED_THRESHOLD) && !isSmartMeter;
        boolean buffered = "50".equals(pkt.recordType());

        String layoutName = resolveLayout(pkt, extended, totalBytes);
        if (layoutName == null) {
            log.warn("No layout found for packet proto={} dev={} type={}", pkt.protocol(), pkt.deviceId(), pkt.recordType());
            return Optional.empty();
        }

        RecordLayout layout = layoutLoader.get(layoutName);
        if (layout == null) return Optional.empty();

        Map<String, Object> values = extractFields(hexData, layout);
        if (values == null) return Optional.empty();

        String deviceId = resolveDeviceId(values, layout, pkt);
        Instant timestamp = resolveTimestamp(hexData, layout, buffered);

        return Optional.of(new InverterData(deviceId, layoutName, timestamp, buffered, values));
    }

    // ── layout selection ──────────────────────────────────────────────────────

    private String resolveLayout(GrowattPacket pkt, boolean extended, int totalBytes) {
        String base = "T" + pkt.protocol() + pkt.deviceId() + pkt.recordType();
        if (extended) base += "X";

        if (layoutLoader.get(base) != null) return base;

        // generic fallback: replace device+rectype with NNNN
        if ("04".equals(pkt.recordType()) || "50".equals(pkt.recordType())) {
            String generic = "T" + pkt.protocol() + "NNNN";
            if (extended) generic += "X";

            // SPH (Single Phase Hybrid) packets are significantly larger than plain extended packets
            if (extended && totalBytes > SPH_THRESHOLD) {
                String sph = generic + "SPH";
                if (layoutLoader.get(sph) != null) return sph;
            }

            if (layoutLoader.get(generic) != null) return generic;
        }
        return null;
    }

    // ── field extraction ──────────────────────────────────────────────────────

    private Map<String, Object> extractFields(String hex, RecordLayout layout) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, FieldDefinition> e : layout.getFields().entrySet()) {
            String key = e.getKey();
            FieldDefinition fd = e.getValue();
            if (!fd.isIncluded() && !props.isIncludeAll()) continue;

            int start = fd.getHexOffset();
            int end   = start + fd.getLength() * 2;
            if (end > hex.length()) {
                log.debug("Layout field {} out of range (need {}, have {})", key, end, hex.length());
                continue;
            }
            String slice = hex.substring(start, end);
            try {
                Object raw = decode(slice, fd.getType());
                Object value = (fd.getDivide() != 1 && raw instanceof Number n)
                        ? n.doubleValue() / fd.getDivide()
                        : raw;
                out.put(key, value);
            } catch (Exception ex) {
                log.warn("Error decoding field {}: {}", key, ex.getMessage());
                return null;
            }
        }
        return out;
    }

    private Object decode(String hexSlice, String type) {
        return switch (type) {
            case "text" -> new String(fromHex(hexSlice), StandardCharsets.UTF_8);
            case "numx" -> {
                byte[] b = fromHex(hexSlice);
                long v = 0;
                for (byte by : b) v = (v << 8) | (by & 0xFF);
                int bits = b.length * 8;
                if (b.length > 0 && (b[0] & 0x80) != 0) v -= (1L << bits);
                yield v;
            }
            default -> Long.parseLong(hexSlice, 16); // "num"
        };
    }

    // ── date/time ─────────────────────────────────────────────────────────────

    private Instant resolveTimestamp(String hex, RecordLayout layout, boolean buffered) {
        FieldDefinition dateDef = layout.getFields().get("date");
        if (dateDef != null && buffered) {
            try {
                int off = dateDef.getHexOffset();
                int yr  = Integer.parseInt(hex.substring(off, off + 2), 16) + 2000;
                int mo  = Integer.parseInt(hex.substring(off + 2, off + 4), 16);
                int dy  = Integer.parseInt(hex.substring(off + 4, off + 6), 16);
                int hr  = Integer.parseInt(hex.substring(off + 6, off + 8), 16);
                int mn  = Integer.parseInt(hex.substring(off + 8, off + 10), 16);
                int sc  = Integer.parseInt(hex.substring(off + 10, off + 12), 16);
                LocalDateTime ldt = LocalDateTime.of(yr, mo, dy, hr, mn, sc);
                return ldt.atZone(ZoneId.systemDefault()).toInstant();
            } catch (Exception ignored) {}
        }
        return Instant.now();
    }

    // ── device id ─────────────────────────────────────────────────────────────

    private String resolveDeviceId(Map<String, Object> values, RecordLayout layout, GrowattPacket pkt) {
        if (values.containsKey("pvserial")) return String.valueOf(values.get("pvserial"));
        if (values.containsKey("datalogserial")) return String.valueOf(values.get("datalogserial"));
        return pkt.deviceId();
    }

    // ── util ──────────────────────────────────────────────────────────────────

    private static byte[] fromHex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(hex, i * 2, i * 2 + 2, 16);
        return out;
    }
}
