package service.music.Core;

import core.CushionBot;
import dev.arbjerg.lavalink.client.Helpers;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.NodeOptions;
import dev.arbjerg.lavalink.client.event.ReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class LavaLink {
    private static final Logger logger = LoggerFactory.getLogger(MusicStreamer.class);
    public static LavalinkClient client = null;

    public static LavalinkClient init() {
        String botToken = CushionBot.BOT_TOKEN;
        client = new LavalinkClient(Helpers.getUserIdFromToken(botToken));
        NodeOptions.Builder builder = new NodeOptions.Builder()
                .setName("local_node_01")
                .setServerUri(URI.create("http://localhost:2333"))
                .setPassword("youshallnotpass");
        client.addNode(builder.build());
        client.on(ReadyEvent.class).blockFirst();
        return client;
    }

    public static void startServer() {
        // execute lavalink.jar
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-Xmx256M", "-jar", "Lavalink.jar");
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process process = pb.start();
            logger.info("Lavalink server started.");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.warn("Shutting down Lavalink server.");
                process.destroy();
            }));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
