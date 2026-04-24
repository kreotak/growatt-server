package org.kreotak.grott.api;

import org.kreotak.grott.config.GrottProperties;
import org.kreotak.grott.output.MqttOutputService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config/mqtt")
public class MqttConfigController {

    private final GrottProperties props;
    private final MqttOutputService mqttService;

    public MqttConfigController(GrottProperties props, MqttOutputService mqttService) {
        this.props = props;
        this.mqttService = mqttService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> get() {
        return ResponseEntity.ok(currentState());
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody Map<String, Object> body) {
        GrottProperties.Mqtt cfg = props.getMqtt();
        try {
            String  host    = str(body, "host",    cfg.getHost());
            int     port    = num(body, "port",    cfg.getPort());
            String  topic   = str(body, "topic",   cfg.getTopic());
            boolean enabled     = bool(body, "enabled",     cfg.isEnabled());
            boolean auth        = bool(body, "auth",        cfg.isAuth());
            String  user        = str(body,  "user",        cfg.getUser());
            String  password    = str(body,  "password",    null);
            boolean haDiscovery = bool(body, "haDiscovery", cfg.isHaDiscovery());

            cfg.setAuth(auth);
            cfg.setUser(user);
            if (password != null && !password.isEmpty()) cfg.setPassword(password);
            cfg.setHaDiscovery(haDiscovery);
            mqttService.applyConfig(host, port, topic, enabled);
            return ResponseEntity.ok(currentState());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> currentState() {
        GrottProperties.Mqtt cfg = props.getMqtt();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled",     cfg.isEnabled());
        m.put("connected",   mqttService.isConnected());
        m.put("host",        cfg.getHost());
        m.put("port",        cfg.getPort());
        m.put("topic",       cfg.getTopic());
        m.put("auth",        cfg.isAuth());
        m.put("user",        cfg.getUser());
        m.put("hasPassword", !cfg.getPassword().isEmpty());
        m.put("haDiscovery", cfg.isHaDiscovery());
        return m;
    }

    private String str(Map<String, Object> m, String key, String fallback) {
        Object v = m.get(key);
        if (v == null) return fallback;
        if (!(v instanceof String)) throw new IllegalArgumentException(key + " must be a string");
        return (String) v;
    }

    private int num(Map<String, Object> m, String key, int fallback) {
        Object v = m.get(key);
        if (v == null) return fallback;
        if (!(v instanceof Number)) throw new IllegalArgumentException(key + " must be a number");
        int i = ((Number) v).intValue();
        if (i < 1 || i > 65535) throw new IllegalArgumentException(key + " must be 1-65535");
        return i;
    }

    private boolean bool(Map<String, Object> m, String key, boolean fallback) {
        Object v = m.get(key);
        if (v == null) return fallback;
        if (!(v instanceof Boolean)) throw new IllegalArgumentException(key + " must be a boolean");
        return (Boolean) v;
    }
}
