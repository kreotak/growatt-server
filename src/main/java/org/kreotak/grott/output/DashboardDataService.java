package org.kreotak.grott.output;

import org.kreotak.grott.parser.InverterData;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DashboardDataService implements OutputService {

    private final ConcurrentHashMap<String, InverterData> latest = new ConcurrentHashMap<>();

    @Override
    public void publish(InverterData data) {
        if (data.buffered()) {
            // Don't let a historical replay packet overwrite a live reading
            latest.putIfAbsent(data.deviceId(), data);
        } else {
            latest.put(data.deviceId(), data);
        }
    }

    public Collection<InverterData> getAll() {
        return latest.values();
    }
}
