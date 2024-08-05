package service.watcher.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import service.watcher.Watcher;

import java.io.IOException;
import java.io.OutputStream;

public class SseHandler extends IntermediateHttpHandler {
    private HttpExchange exchange;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        this.exchange = exchange;
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            return;
        }

        Watcher.register(this);

        // 연결 유지
        try (OutputStream os = exchange.getResponseBody()) {
            while (true) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // 스레드 인터럽트가 발생하면 루프 종료
                    break;
                }
            }
        } finally {
            Watcher.unregister(this);
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