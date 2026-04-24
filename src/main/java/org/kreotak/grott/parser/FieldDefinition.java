package org.kreotak.grott.parser;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One field entry from a layout JSON file.
 * "value" is an offset into the hex-encoded packet string (byte_offset * 2).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldDefinition {

    @JsonProperty("value")
    private int hexOffset;

    @JsonProperty("length")
    private int length = 2;

    @JsonProperty("type")
    private String type = "num";

    @JsonProperty("divide")
    private int divide = 1;

    @JsonProperty("incl")
    private String incl = "yes";

    @JsonProperty("register")
    private Integer register;

    @JsonProperty("pos")
    private Integer pos;

    @JsonProperty("description")
    private String description;

    public int getHexOffset() { return hexOffset; }
    public int getLength()    { return length; }
    public String getType()   { return type; }
    public int getDivide()    { return divide; }
    public boolean isIncluded() { return !"no".equalsIgnoreCase(incl); }
    public Integer getRegister() { return register; }
    public Integer getPos()      { return pos; }
    public String getDescription() { return description; }
}
