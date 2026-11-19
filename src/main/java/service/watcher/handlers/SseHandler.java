package service.watcher.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import service.watcher.Watcher;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SseHandler extends IntermediateHttpHandler {
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    public HttpExchange exchange;
    private OutputStream os;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            super.handle(exchange);
            this.exchange = exchange;
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                System.out.println("option");
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            Watcher.register(this);

            os = exchange.getResponseBody();
            executorService.submit(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            // a data ping keeps proxies from closing the connection and lets the
                            // dashboard notice a dead connection when pings stop arriving
                            write("data: " + PING + "\n\n");
                            Thread.sleep(15000);
                        } catch (IOException e) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    closeConnection();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            String response = "500 (Internal Server Error)\n" + e.getMessage();
            exchange.sendResponseHeaders(500, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    private static final String PING = "{\"type\":\"ping\",\"data\":null}";

    /** Pings and data are written from different threads; keep each message whole. */
    private synchronized void write(String message) throws IOException {
        os.write(message.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        os.flush();
    }

    public void sendData(SseResponse data) {
        if (os != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString(data);
                write("data: " + json + "\n\n");
            } catch (IOException e) {
                closeConnection();
            }
        }
    }

    private void closeConnection() {
        if (os != null) {
            try {
                os.close();
            } catch (IOException e) {
            }
            Watcher.unregister(this);
        }
    }
}