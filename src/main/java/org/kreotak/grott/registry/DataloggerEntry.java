package org.kreotak.grott.registry;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class DataloggerEntry {
    private final String loggerId;
    private volatile String ip;
    private volatile int port;
    private volatile String protocol;
    private final ConcurrentHashMap<String, InverterEntry> inverters = new ConcurrentHashMap<>();

    public DataloggerEntry(String loggerId, String ip, int port, String protocol) {
        this.loggerId = loggerId;
        this.ip = ip;
        this.port = port;
        this.protocol = protocol;
    }

    public void update(String ip, int port, String protocol) {
        this.ip = ip;
        this.port = port;
        this.protocol = protocol;
    }

    public void addInverter(InverterEntry inv) {
        inverters.put(inv.getInverterId(), inv);
    }

    public InverterEntry getInverter(String inverterId) {
        return inverters.get(inverterId);
    }

    public Collection<InverterEntry> getInverters() {
        return inverters.values();
    }

    public boolean hasInverter(String inverterId) {
        return inverters.containsKey(inverterId);
    }

    public String getLoggerId()  { return loggerId; }
    public String getIp()        { return ip; }
    public int    getPort()      { return port; }
    public String getProtocol()  { return protocol; }
}
