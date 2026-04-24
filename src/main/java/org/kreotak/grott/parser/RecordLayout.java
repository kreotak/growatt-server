package org.kreotak.grott.parser;

import java.util.Map;

public class RecordLayout {
    private final String name;
    private final boolean decrypt;
    private final Map<String, FieldDefinition> fields;

    public RecordLayout(String name, boolean decrypt, Map<String, FieldDefinition> fields) {
        this.name    = name;
        this.decrypt = decrypt;
        this.fields  = fields;
    }

    public String getName()                       { return name; }
    public boolean isDecrypt()                    { return decrypt; }
    public Map<String, FieldDefinition> getFields() { return fields; }
}
