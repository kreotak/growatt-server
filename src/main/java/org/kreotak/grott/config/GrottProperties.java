package org.kreotak.grott.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "grott")
public class GrottProperties {

    private boolean verbose = true;
    private String serverHost = "0.0.0.0";
    private int serverPort = 5279;
    private String httpHost = "0.0.0.0";
    private int httpPort = 5782;
    private int inverterResponseWaitMs = 10_000;
    private int dataloggerResponseWaitMs = 5_000;
    private String layoutDir = "classpath:layouts/";
    private boolean includeAll = false;

    private final Mqtt mqtt = new Mqtt();
    private final InfluxDb influxdb = new InfluxDb();
    private final PvOutput pvoutput = new PvOutput();

    public static class Mqtt {
        private boolean enabled = false;
        private String host = "localhost";
        private int port = 1883;
        private String topic = "energy/growatt";
        private String meterTopic = "energy/meter";
        private boolean inverterInTopic = false;
        private boolean retain = false;
        private boolean auth = false;
        private String user = "grott";
        private String password = "growatt2020";
        private boolean haDiscovery = false;
        private String haDiscoveryPrefix = "homeassistant";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        public String getMeterTopic() { return meterTopic; }
        public void setMeterTopic(String meterTopic) { this.meterTopic = meterTopic; }
        public boolean isInverterInTopic() { return inverterInTopic; }
        public void setInverterInTopic(boolean inverterInTopic) { this.inverterInTopic = inverterInTopic; }
        public boolean isRetain() { return retain; }
        public void setRetain(boolean retain) { this.retain = retain; }
        public boolean isAuth() { return auth; }
        public void setAuth(boolean auth) { this.auth = auth; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public boolean isHaDiscovery() { return haDiscovery; }
        public void setHaDiscovery(boolean haDiscovery) { this.haDiscovery = haDiscovery; }
        public String getHaDiscoveryPrefix() { return haDiscoveryPrefix; }
        public void setHaDiscoveryPrefix(String haDiscoveryPrefix) { this.haDiscoveryPrefix = haDiscoveryPrefix; }
    }

    public static class InfluxDb {
        private boolean enabled = false;
        private String url = "http://localhost:8086";
        private String token = "influx_token";
        private String org = "grottorg";
        private String bucket = "grottdb";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getOrg() { return org; }
        public void setOrg(String org) { this.org = org; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
    }

    public static class PvOutput {
        private boolean enabled = false;
        private String apiKey = "yourapikey";
        private String systemId = "yoursystemid";
        private String url = "https://pvoutput.org/service/r2/addstatus.jsp";
        private int rateLimitMinutes = 5;
        private boolean sendTemperature = false;
        private boolean disableV1 = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getSystemId() { return systemId; }
        public void setSystemId(String systemId) { this.systemId = systemId; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public int getRateLimitMinutes() { return rateLimitMinutes; }
        public void setRateLimitMinutes(int rateLimitMinutes) { this.rateLimitMinutes = rateLimitMinutes; }
        public boolean isSendTemperature() { return sendTemperature; }
        public void setSendTemperature(boolean sendTemperature) { this.sendTemperature = sendTemperature; }
        public boolean isDisableV1() { return disableV1; }
        public void setDisableV1(boolean disableV1) { this.disableV1 = disableV1; }
    }

    // --- root getters/setters ---

    public boolean isVerbose() { return verbose; }
    public void setVerbose(boolean verbose) { this.verbose = verbose; }
    public String getServerHost() { return serverHost; }
    public void setServerHost(String serverHost) { this.serverHost = serverHost; }
    public int getServerPort() { return serverPort; }
    public void setServerPort(int serverPort) { this.serverPort = serverPort; }
    public String getHttpHost() { return httpHost; }
    public void setHttpHost(String httpHost) { this.httpHost = httpHost; }
    public int getHttpPort() { return httpPort; }
    public void setHttpPort(int httpPort) { this.httpPort = httpPort; }
    public int getInverterResponseWaitMs() { return inverterResponseWaitMs; }
    public void setInverterResponseWaitMs(int v) { this.inverterResponseWaitMs = v; }
    public int getDataloggerResponseWaitMs() { return dataloggerResponseWaitMs; }
    public void setDataloggerResponseWaitMs(int v) { this.dataloggerResponseWaitMs = v; }
    public String getLayoutDir() { return layoutDir; }
    public void setLayoutDir(String layoutDir) { this.layoutDir = layoutDir; }
    public boolean isIncludeAll() { return includeAll; }
    public void setIncludeAll(boolean includeAll) { this.includeAll = includeAll; }
    public Mqtt getMqtt() { return mqtt; }
    public InfluxDb getInfluxdb() { return influxdb; }
    public PvOutput getPvoutput() { return pvoutput; }
}
