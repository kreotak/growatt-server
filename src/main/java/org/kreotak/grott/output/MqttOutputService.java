package org.kreotak.grott.output;

import tools.jackson.databind.ObjectMapper;
import org.kreotak.grott.config.GrottProperties;
import org.kreotak.grott.parser.InverterData;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MqttOutputService implements OutputService {

    private static final Logger log = LoggerFactory.getLogger(MqttOutputService.class);

    private record SensorDef(String field, String name, String deviceClass, String stateClass, String unit) {}

    private static final List<SensorDef> SENSOR_DEFS = List.of(
        new SensorDef("pvpowerin",        "PV Power In",              "power",       "measurement",       "W"),
        new SensorDef("pv1watt",          "PV String 1 Power",        "power",       "measurement",       "W"),
        new SensorDef("pv2watt",          "PV String 2 Power",        "power",       "measurement",       "W"),
        new SensorDef("pvpowerout",       "PV Power Output",          "power",       "measurement",       "W"),
        new SensorDef("pvgridpower",      "Grid Power L1",            "power",       "measurement",       "W"),
        new SensorDef("pvgridpower2",     "Grid Power L2",            "power",       "measurement",       "W"),
        new SensorDef("pvgridpower3",     "Grid Power L3",            "power",       "measurement",       "W"),
        new SensorDef("pactouserr",       "Power to User",            "power",       "measurement",       "W"),
        new SensorDef("pactousertot",     "Power to User Total",      "power",       "measurement",       "W"),
        new SensorDef("pactogridr",       "Power to Grid",            "power",       "measurement",       "W"),
        new SensorDef("pactogridtot",     "Power to Grid Total",      "power",       "measurement",       "W"),
        new SensorDef("plocaloadr",       "Local Load",               "power",       "measurement",       "W"),
        new SensorDef("plocaloadtot",     "Local Load Total",         "power",       "measurement",       "W"),
        new SensorDef("pdischarge1",      "Battery Discharge Power",  "power",       "measurement",       "W"),
        new SensorDef("p1charge1",        "Battery Charge Power",     "power",       "measurement",       "W"),
        new SensorDef("eactoday",         "AC Energy Today",          "energy",      "total_increasing",  "kWh"),
        new SensorDef("eactotal",         "AC Energy Total",          "energy",      "total_increasing",  "kWh"),
        new SensorDef("epv1today",        "PV1 Energy Today",         "energy",      "total_increasing",  "kWh"),
        new SensorDef("epv1total",        "PV1 Energy Total",         "energy",      "total_increasing",  "kWh"),
        new SensorDef("epv2today",        "PV2 Energy Today",         "energy",      "total_increasing",  "kWh"),
        new SensorDef("epv2total",        "PV2 Energy Total",         "energy",      "total_increasing",  "kWh"),
        new SensorDef("epvtotal",         "PV Energy Total",          "energy",      "total_increasing",  "kWh"),
        new SensorDef("etouser_tod",      "Energy to User Today",     "energy",      "total_increasing",  "kWh"),
        new SensorDef("etouser_tot",      "Energy to User Total",     "energy",      "total_increasing",  "kWh"),
        new SensorDef("etogrid_tod",      "Energy to Grid Today",     "energy",      "total_increasing",  "kWh"),
        new SensorDef("etogrid_tot",      "Energy to Grid Total",     "energy",      "total_increasing",  "kWh"),
        new SensorDef("edischarge1_tod",  "Battery Discharge Today",  "energy",      "total_increasing",  "kWh"),
        new SensorDef("edischarge1_tot",  "Battery Discharge Total",  "energy",      "total_increasing",  "kWh"),
        new SensorDef("eharge1_tod",      "Battery Charge Today",     "energy",      "total_increasing",  "kWh"),
        new SensorDef("eharge1_tot",      "Battery Charge Total",     "energy",      "total_increasing",  "kWh"),
        new SensorDef("elocalload_tod",   "Local Load Today",         "energy",      "total_increasing",  "kWh"),
        new SensorDef("elocalload_tot",   "Local Load Total",         "energy",      "total_increasing",  "kWh"),
        new SensorDef("eacharge_today",   "AC Charge Today",          "energy",      "total_increasing",  "kWh"),
        new SensorDef("eacharge_total",   "AC Charge Total",          "energy",      "total_increasing",  "kWh"),
        new SensorDef("pv1voltage",       "PV String 1 Voltage",      "voltage",     "measurement",       "V"),
        new SensorDef("pv2voltage",       "PV String 2 Voltage",      "voltage",     "measurement",       "V"),
        new SensorDef("pvgridvoltage",    "Grid Voltage L1",          "voltage",     "measurement",       "V"),
        new SensorDef("pvgridvoltage2",   "Grid Voltage L2",          "voltage",     "measurement",       "V"),
        new SensorDef("pvgridvoltage3",   "Grid Voltage L3",          "voltage",     "measurement",       "V"),
        new SensorDef("vbat",             "Battery Voltage",          "voltage",     "measurement",       "V"),
        new SensorDef("pbusvolt",         "P Bus Voltage",            "voltage",     "measurement",       "V"),
        new SensorDef("pv1current",       "PV String 1 Current",      "current",     "measurement",       "A"),
        new SensorDef("pv2current",       "PV String 2 Current",      "current",     "measurement",       "A"),
        new SensorDef("pvgridcurrent",    "Grid Current L1",          "current",     "measurement",       "A"),
        new SensorDef("pvgridcurrent2",   "Grid Current L2",          "current",     "measurement",       "A"),
        new SensorDef("pvgridcurrent3",   "Grid Current L3",          "current",     "measurement",       "A"),
        new SensorDef("pvfrequentie",     "Grid Frequency",           "frequency",   "measurement",       "Hz"),
        new SensorDef("pvtemperature",    "Inverter Temperature",     "temperature", "measurement",       "°C"),
        new SensorDef("pvipmtemperature", "IPM Temperature",          "temperature", "measurement",       "°C"),
        new SensorDef("pvboosttemp",      "Boost Temperature",        "temperature", "measurement",       "°C"),
        new SensorDef("SOC",              "Battery SOC",              "battery",     "measurement",       "%")
    );

    private final GrottProperties props;
    private final ObjectMapper objectMapper;
    private MqttClient client;
    private final Set<String> discoveredDevices = Collections.synchronizedSet(new HashSet<>());

    public MqttOutputService(GrottProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (props.getMqtt().isEnabled()) doConnect();
    }

    @PreDestroy
    public void shutdown() {
        doDisconnect();
    }

    public synchronized void applyConfig(String host, int port, String topic, boolean enabled) {
        GrottProperties.Mqtt cfg = props.getMqtt();
        cfg.setHost(host);
        cfg.setPort(port);
        cfg.setTopic(topic);
        cfg.setEnabled(enabled);
        doDisconnect();
        if (enabled) doConnect();
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    private void doConnect() {
        GrottProperties.Mqtt cfg = props.getMqtt();
        String broker = "tcp://" + cfg.getHost() + ":" + cfg.getPort();
        try {
            client = new MqttClient(broker, "growatt-server-" + System.currentTimeMillis(),
                    new MemoryPersistence());
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setAutomaticReconnect(true);
            opts.setCleanSession(true);
            if (cfg.isAuth()) {
                opts.setUserName(cfg.getUser());
                opts.setPassword(cfg.getPassword().toCharArray());
            }
            client.connect(opts);
            discoveredDevices.clear();
            log.info("MQTT connected to {}", broker);
        } catch (MqttException e) {
            log.error("MQTT connection failed to {}: {}", broker, e.getMessage());
        }
    }

    private void doDisconnect() {
        try {
            if (client != null) {
                if (client.isConnected()) client.disconnect();
                client.close();
                client = null;
            }
        } catch (MqttException ignored) {}
        discoveredDevices.clear();
    }

    @Override
    public void publish(InverterData data) {
        MqttClient c = client;
        if (!props.getMqtt().isEnabled() || c == null || !c.isConnected()) return;
        GrottProperties.Mqtt cfg = props.getMqtt();
        try {
            byte[] json = buildPayload(data);

            String topic = cfg.getTopic();
            if (cfg.isInverterInTopic()) topic += "/" + data.deviceId();
            c.publish(topic, json, 0, cfg.isRetain());

            // HA Discovery requires per-device topic; publish there too if not already done
            if (cfg.isHaDiscovery() && !cfg.isInverterInTopic()) {
                c.publish(cfg.getTopic() + "/" + data.deviceId(), json, 0, cfg.isRetain());
            }

            if (props.isVerbose()) log.info("MQTT published to {}", topic);
            maybePublishDiscovery(data, c);
        } catch (Exception e) {
            log.warn("MQTT publish failed: {}", e.getMessage());
        }
    }

    private byte[] buildPayload(InverterData data) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("device", data.deviceId());
        payload.put("time", data.timestamp().toString());
        payload.put("buffered", data.buffered() ? "yes" : "no");
        payload.put("values", data.values());
        return objectMapper.writeValueAsBytes(payload);
    }

    private void maybePublishDiscovery(InverterData data, MqttClient c) {
        GrottProperties.Mqtt cfg = props.getMqtt();
        if (!cfg.isHaDiscovery()) return;
        if (!discoveredDevices.add(data.deviceId())) return;

        String deviceId = data.deviceId();
        String stateTopic = cfg.getTopic() + "/" + deviceId;
        String prefix = cfg.getHaDiscoveryPrefix();

        Map<String, Object> device = new LinkedHashMap<>();
        device.put("identifiers", List.of("growatt-server_" + deviceId));
        device.put("name", "Growatt " + deviceId);
        device.put("model", "Growatt SPH");
        device.put("manufacturer", "Growatt");

        int published = 0;
        for (SensorDef sd : SENSOR_DEFS) {
            if (!data.values().containsKey(sd.field())) continue;

            String objectId = "growatt-server_" + deviceId + "_" + sd.field();
            String discoveryTopic = prefix + "/sensor/" + objectId + "/config";

            Map<String, Object> config = new LinkedHashMap<>();
            config.put("name", sd.name());
            config.put("unique_id", objectId);
            config.put("state_topic", stateTopic);
            config.put("value_template", "{{ value_json['values']['" + sd.field() + "'] }}");
            if (sd.unit() != null)        config.put("unit_of_measurement", sd.unit());
            if (sd.deviceClass() != null) config.put("device_class", sd.deviceClass());
            if (sd.stateClass() != null)  config.put("state_class", sd.stateClass());
            config.put("device", device);

            try {
                byte[] json = objectMapper.writeValueAsBytes(config);
                c.publish(discoveryTopic, json, 1, true);
                published++;
            } catch (Exception e) {
                log.warn("HA discovery publish failed for {}: {}", sd.field(), e.getMessage());
            }
        }
        log.info("Published {} HA MQTT discovery configs for device {}", published, deviceId);
    }
}
