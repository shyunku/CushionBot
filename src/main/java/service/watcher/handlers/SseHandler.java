package service.watcher.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import service.watcher.SseClient;
import service.watcher.Watcher;

import java.io.IOException;

/** Opens an SSE connection: one {@link SseClient} per request, kept open after this handler returns. */
public class SseHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }
        SseClient client = new SseClient(exchange);
        if (!Watcher.register(client)) {
            HttpUtil.sendText(exchange, 503, "Too many connections");
            return;
        }
        try {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, 0);
            client.offer(":\n\n");
        } catch (IOException e) {
            client.close();
        }
    }
}
