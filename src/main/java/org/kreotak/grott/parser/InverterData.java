package org.kreotak.grott.parser;

import java.time.Instant;
import java.util.Map;

public record InverterData(
        String deviceId,
        String layoutName,
        Instant timestamp,
        boolean buffered,
        Map<String, Object> values   // field name → Number or String
) {}
