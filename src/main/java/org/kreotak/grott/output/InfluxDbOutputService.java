package org.kreotak.grott.output;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.kreotak.grott.config.GrottProperties;
import org.kreotak.grott.parser.InverterData;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "grott.influxdb", name = "enabled", havingValue = "true")
public class InfluxDbOutputService implements OutputService {

    private static final Logger log = LoggerFactory.getLogger(InfluxDbOutputService.class);

    private final GrottProperties props;
    private InfluxDBClient influxClient;

    public InfluxDbOutputService(GrottProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void connect() {
        GrottProperties.InfluxDb cfg = props.getInfluxdb();
        influxClient = InfluxDBClientFactory.create(cfg.getUrl(), cfg.getToken().toCharArray(),
                cfg.getOrg(), cfg.getBucket());
        log.info("InfluxDB client initialised for {}", cfg.getUrl());
    }

    @PreDestroy
    public void close() {
        if (influxClient != null) influxClient.close();
    }

    @Override
    public void publish(InverterData data) {
        if (influxClient == null) return;
        try {
            Point point = Point.measurement(data.deviceId())
                    .time(data.timestamp(), WritePrecision.S);

            for (Map.Entry<String, Object> e : data.values().entrySet()) {
                Object v = e.getValue();
                if (v instanceof Number n) point.addField(e.getKey(), n.doubleValue());
                else if (v instanceof String s) point.addField(e.getKey(), s);
            }

            WriteApiBlocking api = influxClient.getWriteApiBlocking();
            api.writePoint(point);
            if (props.isVerbose()) log.info("InfluxDB point written for {}", data.deviceId());
        } catch (Exception e) {
            log.error("InfluxDB write failed: {}", e.getMessage());
        }
    }
}
