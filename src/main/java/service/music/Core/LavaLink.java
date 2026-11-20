package service.music.Core;

import core.CushionBot;
import dev.arbjerg.lavalink.client.Helpers;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.NodeOptions;
import dev.arbjerg.lavalink.client.event.ReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs the Lavalink server (Lavalink.jar + application.yml in the working directory) as a child process
 * and connects the client to it. Port and password are read from application.yml so both sides agree.
 */
public class LavaLink {
    private static final Logger logger = LoggerFactory.getLogger(LavaLink.class);
    private static final Duration READY_TIMEOUT = Duration.ofMinutes(3);
    private static final long RESTART_DELAY_SECONDS = 5;
    public static LavalinkClient client = null;
    private static volatile boolean shuttingDown = false;

    public static LavalinkClient init() {
        String botToken = CushionBot.BOT_TOKEN;
        client = new LavalinkClient(Helpers.getUserIdFromToken(botToken));
        NodeOptions.Builder builder = new NodeOptions.Builder()
                .setName("local_node_01")
                .setServerUri(URI.create("http://127.0.0.1:" + configValue("server.port", "2333")))
                .setPassword(configValue("lavalink.server.password", "youshallnotpass"));
        client.addNode(builder.build());
        MusicEvents.subscribe(client);
        // fail fast (systemd restarts the bot) instead of hanging forever when Lavalink never comes up
        client.on(ReadyEvent.class).blockFirst(READY_TIMEOUT);
        return client;
    }

    /** A dotted key from application.yml, or the default when the file or key is missing. */
    @SuppressWarnings("unchecked")
    static String configValue(String dottedKey, String fallback) {
        File file = new File("application.yml");
        if (!file.exists()) return fallback;
        try (InputStream in = new FileInputStream(file)) {
            Object node = new Yaml().load(in);
            for (String part : dottedKey.split("\\.")) {
                if (!(node instanceof Map)) return fallback;
                node = ((Map<String, Object>) node).get(part);
            }
            return node == null ? fallback : String.valueOf(node);
        } catch (Exception e) {
            logger.warn("Could not read {} from application.yml: {}", dottedKey, e.getMessage());
            return fallback;
        }
    }

    public static void startServer() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shuttingDown = true));
        launch();
    }

    /** Start Lavalink and start it again if it exits on its own (e.g. out of memory). */
    private static void launch() {
        try {
            ProcessBuilder pb = new ProcessBuilder(javaBinary(), "-Xmx256M", "-jar", "Lavalink.jar");
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process process = pb.start();
            logger.info("Lavalink server started.");

            Thread stop = new Thread(() -> {
                logger.warn("Shutting down Lavalink server.");
                process.destroy();
            });
            Runtime.getRuntime().addShutdownHook(stop);

            process.onExit().thenAccept(p -> {
                if (shuttingDown) return;
                logger.error("Lavalink server exited with code {}; restarting in {}s", p.exitValue(), RESTART_DELAY_SECONDS);
                try {
                    Runtime.getRuntime().removeShutdownHook(stop);
                } catch (IllegalStateException ignored) {
                }
                try {
                    TimeUnit.SECONDS.sleep(RESTART_DELAY_SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (!shuttingDown) launch();
            });
        } catch (Exception e) {
            logger.error("Could not start the Lavalink server", e);
        }
    }

    /** The java running this bot, so Lavalink does not depend on PATH. */
    private static String javaBinary() {
        File java = new File(System.getProperty("java.home"), "bin/java");
        return java.exists() ? java.getPath() : "java";
    }
}
