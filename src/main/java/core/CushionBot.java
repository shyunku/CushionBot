package core;

import Utilities.TokenManager;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.libraries.jda.JDAVoiceUpdateListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import service.inmemory.RedisClient;
import service.music.Core.LavaLink;
import service.watcher.Watcher;

public class CushionBot {
    public static final String BOT_TOKEN = new TokenManager().getDiscordBotToken();
    public static JDA jda;
    public static LavalinkClient lavalinkClient;

    public static void main(String[] args) {
        try {
            RedisClient redisClient = new RedisClient();
            redisClient.connect();
            Watcher.start();
            LavaLink.startServer();
            lavalinkClient = LavaLink.init();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

        jda = JDABuilder.createDefault(BOT_TOKEN)
                .enableIntents(GatewayIntent.GUILD_MESSAGES)
                .enableIntents(GatewayIntent.DIRECT_MESSAGES)
                .enableIntents(GatewayIntent.GUILD_VOICE_STATES)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(new InternalEventListener())
                .setVoiceDispatchInterceptor(new JDAVoiceUpdateListener(lavalinkClient))
                .build();
    }
}
