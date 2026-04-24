package org.kreotak.grott.output;

import org.kreotak.grott.config.GrottProperties;
import org.kreotak.grott.parser.InverterData;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(prefix = "grott.pvoutput", name = "enabled", havingValue = "true")
public class PvOutputService implements OutputService {

    private static final Logger log = LoggerFactory.getLogger(PvOutputService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final GrottProperties props;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ConcurrentHashMap<String, Long> lastSent = new ConcurrentHashMap<>();

    public PvOutputService(GrottProperties props) {
        this.props = props;
    }

    @PreDestroy
    public void shutdown() {
        http.close();
    }

    @Override
    public void publish(InverterData data) {
        GrottProperties.PvOutput cfg = props.getPvoutput();

        long nowMs = System.currentTimeMillis();
        long limitMs = (long) cfg.getRateLimitMinutes() * 60_000;
        Long last = lastSent.get(data.deviceId());
        if (last != null && nowMs - last < limitMs) {
            log.debug("PVOutput rate-limited for {}", data.deviceId());
            return;
        }
        lastSent.put(data.deviceId(), nowMs);

        Map<String, Object> v = data.values();
        if (!v.containsKey("pvpowerout") || !v.containsKey("pvgridvoltage")) return;

        ZonedDateTime zdt = data.timestamp().atZone(ZoneId.systemDefault());
        String date = zdt.format(DATE_FMT);
        String time = zdt.format(TIME_FMT);

        // Values in data.values() are already scaled by the layout divide factor.
        // pvpowerout → W, pvgridvoltage → V, pvenergytoday → kWh, pvtemperature → °C
        StringBuilder body = new StringBuilder();
        body.append("d=").append(date).append("&t=").append(time);
        body.append("&v2=").append((long) numVal(v, "pvpowerout"));
        body.append("&v6=").append(numVal(v, "pvgridvoltage"));
        if (!cfg.isDisableV1() && v.containsKey("pvenergytoday")) {
            body.append("&v1=").append((long)(numVal(v, "pvenergytoday") * 1000)); // kWh → Wh
        }
        if (cfg.isSendTemperature() && v.containsKey("pvtemperature")) {
            body.append("&v5=").append(numVal(v, "pvtemperature"));
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.getUrl()))
                    .header("X-Pvoutput-Apikey", cfg.getApiKey())
                    .header("X-Pvoutput-SystemId", cfg.getSystemId())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (props.isVerbose()) log.info("PVOutput response: {}", resp.body());
        } catch (Exception e) {
            log.warn("PVOutput post failed: {}", e.getMessage());
        }
    }

    private double numVal(Map<String, Object> v, String key) {
        Object o = v.get(key);
        return o instanceof Number n ? n.doubleValue() : 0;
    }
}
