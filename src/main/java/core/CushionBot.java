package core;

import Utilities.TokenManager;
import service.inmemory.RedisClient;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

import javax.security.auth.login.LoginException;

public class CushionBot {
    private static final String BOT_TOKEN = new TokenManager().getDiscordBotToken();

    private static JDA jda;

    public static void main(String[] args) throws LoginException {
        try {
            RedisClient redisClient = new RedisClient();
            redisClient.connect();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

        jda = JDABuilder.createDefault(BOT_TOKEN)
                .enableIntents(GatewayIntent.GUILD_MESSAGES)
                .enableIntents(GatewayIntent.DIRECT_MESSAGES)
                .addEventListeners(new InternalEventListener())
                .build();
    }
}
