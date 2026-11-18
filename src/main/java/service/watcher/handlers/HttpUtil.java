package service.watcher.handlers;

import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

public class HttpUtil {
    private static final int GZIP_MIN_BYTES = 1024;

    public static void send(HttpExchange exchange, int status, String contentType, byte[] body, String cacheControl) throws IOException {
        if (contentType != null) exchange.getResponseHeaders().set("Content-Type", contentType);
        if (cacheControl != null) exchange.getResponseHeaders().set("Cache-Control", cacheControl);

        String acceptEncoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
        if (body.length >= GZIP_MIN_BYTES && isCompressible(contentType) && acceptEncoding != null && acceptEncoding.contains("gzip")) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(body.length / 3);
            try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
                gzip.write(body);
            }
            body = buffer.toByteArray();
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.getResponseHeaders().set("Vary", "Accept-Encoding");
        }

        if (exchange.getRequestMethod().equalsIgnoreCase("HEAD")) {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    public static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        send(exchange, status, "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8), "no-store");
    }

    public static void sendJson(HttpExchange exchange, String json) throws IOException {
        send(exchange, 200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8), "no-store");
    }

    private static boolean isCompressible(String contentType) {
        if (contentType == null) return false;
        return contentType.startsWith("text/") || contentType.startsWith("application/json")
                || contentType.startsWith("application/javascript") || contentType.startsWith("image/svg+xml");
    }
}
