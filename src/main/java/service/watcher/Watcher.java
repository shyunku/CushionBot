package service.watcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.watcher.handlers.*;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Dashboard HTTP server (port 7918): built dashboard, REST API and server-sent events.
 * Events are broadcast without blocking the caller: each SSE connection has its own bounded queue,
 * drained by a small writer pool, so a slow or stuck browser never holds up JDA event threads.
 */
public class Watcher {
    private static final Logger logger = LoggerFactory.getLogger(Watcher.class);
    public static final String WATCH_TYPE_CHANGE = "change";
    /** Sent after the bot finishes loading; dashboards reload everything. */
    public static final String WATCH_TYPE_RESYNC = "resync";
    /** Upper bound on simultaneous SSE connections from the public port. */
    public static final int MAX_SSE_CLIENTS = 200;
    private static final long PING_SECONDS = 15;

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Set<SseClient> clients = ConcurrentHashMap.newKeySet();
    static final ExecutorService writers = Executors.newFixedThreadPool(4, daemon("sse-writer"));
    private static final ScheduledExecutorService pinger = Executors.newSingleThreadScheduledExecutor(daemon("sse-ping"));
    private static final String PING = "data: {\"type\":\"ping\",\"data\":null}\n\n";

    private static ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    public static void start() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(7918), 0);
        server.createContext("/api", new ApiHandler());
        server.createContext("/sse", new SseHandler());
        server.createContext("/", new StaticFileHandler(new File("watcher/dist")));
        // SSE handlers return right away, so a fixed pool is enough and bounds threads from the public port
        server.setExecutor(Executors.newFixedThreadPool(16, daemon("watcher-http")));
        server.start();
        // a data ping keeps proxies from closing idle connections and lets dashboards notice dead ones
        pinger.scheduleAtFixedRate(() -> broadcast(PING), PING_SECONDS, PING_SECONDS, TimeUnit.SECONDS);
        logger.info("Watch server (dashboard + API) started at port 7918");
    }

    /** Returns false when the connection limit is reached. */
    public static boolean register(SseClient client) {
        if (clients.size() >= MAX_SSE_CLIENTS) return false;
        clients.add(client);
        return true;
    }

    public static void unregister(SseClient client) {
        clients.remove(client);
    }

    public static void sendData(SseResponse res) {
        try {
            broadcast("data: " + mapper.writeValueAsString(res) + "\n\n");
        } catch (Exception e) {
            logger.error("Could not serialize SSE message", e);
        }
    }

    private static void broadcast(String message) {
        for (SseClient client : clients) {
            client.offer(message);
        }
    }
}
