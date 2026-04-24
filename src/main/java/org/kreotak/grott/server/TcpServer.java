package org.kreotak.grott.server;

import org.kreotak.grott.config.GrottProperties;
import org.kreotak.grott.output.OutputService;
import org.kreotak.grott.parser.RecordParser;
import org.kreotak.grott.registry.DeviceRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TcpServer {

    private static final Logger log = LoggerFactory.getLogger(TcpServer.class);

    private final GrottProperties props;
    private final DeviceRegistry registry;
    private final RecordParser parser;
    private final List<OutputService> outputs;

    private ServerSocket serverSocket;
    private volatile boolean running;

    /** Active handlers keyed by "ip_port". */
    final ConcurrentHashMap<String, InverterHandler> handlers = new ConcurrentHashMap<>();

    public TcpServer(GrottProperties props, DeviceRegistry registry,
                     RecordParser parser, List<OutputService> outputs) {
        this.props    = props;
        this.registry = registry;
        this.parser   = parser;
        this.outputs  = outputs;
    }

    @PostConstruct
    public void start() throws IOException {
        serverSocket = new ServerSocket(props.getServerPort());
        serverSocket.setReuseAddress(true);
        running = true;
        log.info("TCP server listening on {}:{}", props.getServerHost(), props.getServerPort());
        Thread.ofVirtual().name("grott-acceptor").start(this::acceptLoop);
    }

    @PreDestroy
    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        log.info("TCP server stopped");
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                client.setKeepAlive(true);
                log.info("TCP connection accepted from {}:{}", client.getInetAddress().getHostAddress(), client.getPort());
                InverterHandler handler = new InverterHandler(client, props, registry, parser, outputs);
                handlers.put(handler.getClientKey(), handler);
                handler.setOnClose(() -> handlers.remove(handler.getClientKey()));
                handler.start();
            } catch (IOException e) {
                if (running) log.error("Accept error: {}", e.getMessage());
            }
        }
    }

    public int getActiveConnectionCount() {
        return handlers.size();
    }

    /**
     * Enqueue a command to be sent to the datalogger identified by its TCP connection key.
     */
    public boolean enqueueCommand(String ip, int port, byte[] command) {
        String key = ip + "_" + port;
        InverterHandler h = handlers.get(key);
        if (h == null) return false;
        h.sendQueue.add(command);
        return true;
    }
}
