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
                            os.write(":\n\n".getBytes()); // 주기적으로 ping을 보내 연결을 유지
                            os.flush();
                            Thread.sleep(1000);
                        } catch (IOException e) {
                            Thread.currentThread().interrupt();
                            closeConnection();
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

    public void sendData(SseResponse data) {
        if (os != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString(data);
                String message = "data: " + json + "\n\n";
                os.write(message.getBytes());
                os.flush();
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
                e.printStackTrace();
            }
            Watcher.unregister(this);
        }
    }
}