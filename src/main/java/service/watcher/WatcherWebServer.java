package service.watcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class WatcherWebServer {
    private static final Logger logger = LoggerFactory.getLogger(WatcherWebServer.class);
    private static final File WATCHER_DIR = new File("watcher");

    public static void startServer() {
        // execute watcher web dashboard (watcher/main.js); the bot keeps running even if it fails
        if (!new File(WATCHER_DIR, "node_modules").isDirectory()) {
            logger.warn("Watcher web server not started: run 'pnpm install' in {} first.", WATCHER_DIR.getAbsolutePath());
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("node", "main.js");
            pb.directory(WATCHER_DIR);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process process = pb.start();
            logger.info("Watcher web server started.");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.warn("Shutting down watcher web server.");
                process.destroy();
            }));
        } catch (Exception e) {
            logger.error("Failed to start watcher web server.", e);
        }
    }
}
