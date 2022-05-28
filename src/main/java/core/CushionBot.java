package core;

import Utilities.TokenManager;
import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;

import javax.security.auth.login.LoginException;

public class CushionBot {
    private static final String BOT_TOKEN = new TokenManager().getDiscordBotToken();

    private static JDA jda;

    public static void main(String[] args) throws LoginException {
        jda = JDABuilder.createDefault(BOT_TOKEN)
                .addEventListeners(new InternalEventListener())
                .build();
    }
}
