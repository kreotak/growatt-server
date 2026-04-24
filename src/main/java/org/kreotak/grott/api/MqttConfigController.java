package org.kreotak.grott.api;

import org.kreotak.grott.config.GrottProperties;
import org.kreotak.grott.output.MqttOutputService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        GrottProperties.Mqtt cfg = props.getMqtt();
        return ResponseEntity.ok(Map.of(
                "enabled",   cfg.isEnabled(),
                "connected", mqttService.isConnected(),
                "host",      cfg.getHost(),
                "port",      cfg.getPort()
        ));
    }
}
