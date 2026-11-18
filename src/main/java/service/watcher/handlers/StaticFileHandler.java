package service.watcher.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

/**
 * Serves the built dashboard (watcher/dist). Unknown paths fall back to index.html
 * so client-side routes such as /servers/{id} or /channel/{id} work on reload.
 */
public class StaticFileHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(StaticFileHandler.class);
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("js", "application/javascript; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("txt", "text/plain; charset=utf-8")
    );

    private final File root;

    public StaticFileHandler(File root) {
        this.root = root;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (!method.equalsIgnoreCase("GET") && !method.equalsIgnoreCase("HEAD")) {
                HttpUtil.sendText(exchange, 405, "Method Not Allowed");
                return;
            }

            File index = new File(root, "index.html");
            if (!index.isFile()) {
                HttpUtil.sendText(exchange, 503, "Watcher dashboard is not built. Run: cd watcher && pnpm install && pnpm build");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            File file = new File(root, path).getCanonicalFile();
            boolean inside = file.getPath().startsWith(root.getCanonicalPath() + File.separator);
            if (!inside || !file.isFile()) {
                // client-side route
                HttpUtil.send(exchange, 200, TYPES.get("html"), Files.readAllBytes(index.toPath()), "no-cache");
                return;
            }

            String name = file.getName();
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
            String cache = path.startsWith("/assets/") ? "public, max-age=31536000, immutable" : "no-cache";
            HttpUtil.send(exchange, 200, TYPES.getOrDefault(ext, "application/octet-stream"), Files.readAllBytes(file.toPath()), cache);
        } catch (Exception e) {
            logger.error("Static file error: {}", exchange.getRequestURI(), e);
            HttpUtil.sendText(exchange, 500, "Internal Server Error");
        }
    }
}
