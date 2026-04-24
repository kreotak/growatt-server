package org.kreotak.grott.registry;

public class InverterEntry {
    private final String inverterId;
    private final String inverterNo; // 2 hex chars (byte 6 of registration packet)
    private volatile double lastPower;

    public InverterEntry(String inverterId, String inverterNo) {
        this.inverterId = inverterId;
        this.inverterNo = inverterNo;
    }

    public String getInverterId() { return inverterId; }
    public String getInverterNo() { return inverterNo; }
    public double getLastPower() { return lastPower; }
    public void setLastPower(double lastPower) { this.lastPower = lastPower; }
}
