package org.kreotak.grott.api;

import org.kreotak.grott.output.DashboardDataService;
import org.kreotak.grott.parser.InverterData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class DashboardController {

    private final DashboardDataService dashboardData;

    public DashboardController(DashboardDataService dashboardData) {
        this.dashboardData = dashboardData;
    }

    @GetMapping("/api/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        List<Map<String, Object>> inverters = dashboardData.getAll().stream()
                .map(this::toMap)
                .toList();
        return ResponseEntity.ok(Map.of("inverters", inverters));
    }

    private Map<String, Object> toMap(InverterData d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deviceId",    d.deviceId());
        m.put("layout",      d.layoutName());
        m.put("timestamp",   d.timestamp().toString());
        m.put("buffered",    d.buffered());

        pick(m, d, "pvstatus", "pvpowerin", "pvpowerout",
                "pv1voltage", "pv1current", "pv1watt",
                "pv2voltage", "pv2current", "pv2watt",
                "pvgridvoltage", "pvgridcurrent", "pvgridpower",
                "pvgridvoltage2", "pvgridcurrent2", "pvgridpower2",
                "pvgridvoltage3", "pvgridcurrent3", "pvgridpower3",
                "pvfrequency", "pvfrequentie",
                "pvtemperature", "pvboosttemp", "boosttemperature",
                "eactoday", "pvenergytoday", "eactotal", "pvenergytotal",
                "epv1today", "epv1total", "epv2today", "epv2total",
                // SPH hybrid fields
                "SOC", "vbat", "bat_dsp", "p1charge1", "pdischarge1",
                "plocaloadr", "plocaloadtot",
                "pactouserr", "pactousertot", "pactogridr", "pactogridtot",
                "etouser_tod", "etouser_tot", "etogrid_tod", "etogrid_tot",
                "edischarge1_tod", "edischarge1_tot", "eharge1_tod", "eharge1_tot",
                "elocalload_tod", "elocalload_tot", "eacharge_today", "eacharge_total");
        m.put("allValues", d.values());
        return m;
    }

    private void pick(Map<String, Object> out, InverterData d, String... keys) {
        for (String key : keys) {
            Object val = d.values().get(key);
            if (val != null) out.put(key, val);
        }
    }
}
