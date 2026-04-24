package org.kreotak.grott.parser;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads all layout JSON files from the classpath layouts/ directory.
 *
 * Each file may contain one or more layout objects keyed by layout name,
 * e.g. {"T06NNNNXMOD": { ... field defs ... }}.
 *
 * Special keys "decrypt", "date", "logstart", "device" are handled separately
 * and excluded from the field map.
 */
@Component
public class LayoutLoader {

    private static final Logger log = LoggerFactory.getLogger(LayoutLoader.class);

    private static final java.util.Set<String> META_KEYS =
            java.util.Set.of("decrypt", "date", "logstart", "device");

    private final Map<String, RecordLayout> layouts = new HashMap<>();

    public LayoutLoader(ObjectMapper objectMapper) throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:layouts/*.json");
        for (Resource r : resources) {
            try (InputStream in = r.getInputStream()) {
                load(in, objectMapper, r.getFilename());
            }
        }
        log.info("Loaded {} record layouts: {}", layouts.size(), layouts.keySet());
    }

    private void load(InputStream in, ObjectMapper mapper, String fileName) throws Exception {
        // Raw map: layoutName → { fieldName → {value,length,type,...} }
        Map<String, Map<String, Object>> raw =
                mapper.readValue(in, new TypeReference<>() {});

        for (Map.Entry<String, Map<String, Object>> entry : raw.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Object> fieldMap = entry.getValue();

            boolean decrypt = true;
            int dateOffset  = 0;
            Map<String, FieldDefinition> fields = new LinkedHashMap<>();

            for (Map.Entry<String, Object> fe : fieldMap.entrySet()) {
                String key = fe.getKey();
                if ("decrypt".equals(key)) {
                    Object val = ((Map<?, ?>) fe.getValue()).get("value");
                    decrypt = !"false".equalsIgnoreCase(String.valueOf(val));
                    continue;
                }
                if (META_KEYS.contains(key)) continue;

                FieldDefinition fd = mapper.convertValue(fe.getValue(), FieldDefinition.class);
                fields.put(key, fd);
            }

            layouts.put(layoutName, new RecordLayout(layoutName, decrypt, fields));
            log.debug("Loaded layout {} from {}", layoutName, fileName);
        }
    }

    public Map<String, RecordLayout> getLayouts() { return layouts; }

    public RecordLayout get(String name) { return layouts.get(name); }
}
