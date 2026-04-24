# growatt-server

Java/Spring Boot reimplementation of [grottserver](https://github.com/johanmeijer/grott) for Growatt solar inverter monitoring.

Listens for Growatt datalogger packets on a TCP/UDP port, decodes them using layout JSON files, and exposes the data via a REST API, a live dashboard, and optional MQTT/InfluxDB/PVOutput forwarding.

---

## Ports

| Port | Protocol | Purpose |
|------|----------|---------|
| `5279` | TCP + UDP | Growatt datalogger (inverter data) |
| `5782` | HTTP | Dashboard + REST API |

Change via `application.yml` (`grott.server-port` / `server.port`).

---

## Running

```bash
java -jar growatt-server.jar
```

Or with Docker, pass environment variables directly (see below).

---

## Environment variables

All variables are optional. Defaults are shown.

### MQTT

| Variable | Default | Description |
|----------|---------|-------------|
| `MQTT_ENABLED` | `false` | Enable MQTT publishing |
| `MQTT_HOST` | `localhost` | MQTT broker hostname or IP |
| `MQTT_PORT` | `1883` | MQTT broker port |
| `MQTT_TOPIC` | `energy/growatt` | Base topic for inverter data |
| `MQTT_METER_TOPIC` | `energy/meter` | Topic for meter data |
| `MQTT_INVERTER_IN_TOPIC` | `false` | Append device ID to topic (`energy/growatt/<id>`) |
| `MQTT_RETAIN` | `false` | Publish with MQTT retain flag |
| `MQTT_AUTH` | `false` | Enable username/password authentication |
| `MQTT_USER` | `grott` | MQTT username (used when `MQTT_AUTH=true`) |
| `MQTT_PASSWORD` | _(empty)_ | MQTT password (used when `MQTT_AUTH=true`) |

### Home Assistant MQTT Discovery

| Variable | Default | Description |
|----------|---------|-------------|
| `MQTT_HA_DISCOVERY` | `false` | Publish HA MQTT Discovery configs on first data packet |
| `MQTT_HA_DISCOVERY_PREFIX` | `homeassistant` | Discovery topic prefix (must match HA config) |

When `MQTT_HA_DISCOVERY=true` the server publishes retained sensor config messages to:

```
<prefix>/sensor/growatt-server_<deviceId>_<field>/config
```

Home Assistant picks these up automatically and creates entities grouped under a single *Growatt \<deviceId\>* device. Covered sensors include power (W), energy (kWh, `total_increasing` for the Energy dashboard), voltage, current, frequency, temperature, and battery SOC.

Data is always published per-device at `energy/growatt/<deviceId>` when discovery is active so HA can subscribe to the correct topic.

### MQTT — Docker example

```yaml
environment:
  MQTT_ENABLED: "true"
  MQTT_HOST: "192.168.1.10"
  MQTT_PORT: "1883"
  MQTT_TOPIC: "energy/growatt"
  MQTT_AUTH: "true"
  MQTT_USER: "myuser"
  MQTT_PASSWORD: "mypassword"
  MQTT_HA_DISCOVERY: "true"
```

---

## Dashboard

Open `http://<host>:5782` in a browser. The dashboard shows live data and an animated energy flow diagram (Solar → Home → Battery → Grid). MQTT connection state is shown in the header when MQTT is enabled.

---

## REST API

| Endpoint | Description |
|----------|-------------|
| `GET /api/inverters` | List all known device IDs |
| `GET /api/inverter/{id}` | Latest data for a device |
| `GET /api/config/mqtt` | Current MQTT connection state |
| `GET /actuator/health` | Spring Boot health check |

---

## Layouts

Packet layouts are loaded from `src/main/resources/layouts/`.  
Currently included:

| File | Inverter type |
|------|---------------|
| `T06NNNNX.json` | Standard Growatt string inverter |
| `T06NNNNXSPH.json` | SPH hybrid inverter (battery + grid export) |

The SPH layout is selected automatically when the packet length exceeds 460 bytes.
