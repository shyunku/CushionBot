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
    private HttpExchange exchange;

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
            System.out.println("connected");

            Watcher.register(this);

            executorService.submit(() -> {
                try (OutputStream os = exchange.getResponseBody()) {
                    while (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(500); // 유휴 시간 설정
                    }
                } catch (InterruptedException | IOException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    Watcher.unregister(this);
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

    public void sendData(SseResponse data) {
        if (exchange != null && exchange.getResponseBody() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString(data);
                exchange.getResponseBody().write(json.getBytes());
                exchange.getResponseBody().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}