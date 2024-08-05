package service.watcher.handlers;

import com.sun.net.httpserver.HttpExchange;
import service.watcher.GuildWatcher;

import java.io.IOException;
import java.io.OutputStream;

public class DataHandler extends IntermediateHttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            super.handle(exchange);
            String dataJson = GuildWatcher.getAccessLogs();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, dataJson.getBytes().length);

            OutputStream os = exchange.getResponseBody();
            os.write(dataJson.getBytes());
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}