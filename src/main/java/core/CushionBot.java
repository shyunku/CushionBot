package core;

import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;

import javax.security.auth.login.LoginException;

public class CushionBot {
    private static final String BOT_TOKEN = "Njc0NDIxMzAwNzM0ODUzMTQz.Xm-VBA.XjWl6PqCBBuTBXc3AqlgI-6WWdc";

    private static JDA jda;

    public static void main(String[] args){
        JDABuilder jb = new JDABuilder(AccountType.BOT);
        jb.setAutoReconnect(true);
        jb.setStatus(OnlineStatus.ONLINE);
        jb.setToken(BOT_TOKEN);
        jb.addEventListeners(new InternalEventListener());

        try {
            jda = jb.build();
        } catch (LoginException e) {
            e.printStackTrace();
        }
    }
}
