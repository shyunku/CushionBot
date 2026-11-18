package service.watcher;

import com.sun.net.httpserver.HttpServer;
import service.watcher.handlers.*;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

public class Watcher {
    public static final String WATCH_TYPE_CHANGE = "change";
    private static final Set<SseHandler> clients = Collections.synchronizedSet(new HashSet<>());

    public static void start() throws Exception {
        startServer();
    }

    private static void startServer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(7918), 0);
        server.createContext("/api", new ApiHandler());
        server.createContext("/sse", new SseHandler());
        server.createContext("/", new StaticFileHandler(new File("watcher/dist")));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Watch server (dashboard + API) started at port 7918");
    }

    public static void register(SseHandler handler) {
        System.out.println("Client connected: " + handler.exchange.getRemoteAddress());
        clients.add(handler);
    }

    public static void unregister(SseHandler handler) {
        System.out.println("Client disconnected: " + handler.exchange.getRemoteAddress());
        clients.remove(handler);
    }

    public static void sendData(SseResponse res) {
//        System.out.println("Sending data to " + clients.size() + " clients");
        synchronized (clients) {
            for (SseHandler client : clients) {
                client.sendData(res);
            }
        }
    }
}
