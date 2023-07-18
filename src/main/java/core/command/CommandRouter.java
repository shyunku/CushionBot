package core.command;

import core.Service;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;

public class CommandRouter {
    private final Logger logger = LoggerFactory.getLogger(CommandRouter.class);

    public CommandRouter() {}

    public void parseCommand(MessageReceivedEvent e){
        Guild guild = e.getGuild();
        String guildId = guild.getId();

        if(!Service.guildCommandRouters.containsKey(guildId)) {
            GuildCommandRouter newGuildCommandRouter = new GuildCommandRouter(guild);
            Service.guildCommandRouters.put(guildId, newGuildCommandRouter);
        }

        GuildCommandRouter guildCommandRouter = Service.guildCommandRouters.get(guildId);
        guildCommandRouter.route(e);
    }
}