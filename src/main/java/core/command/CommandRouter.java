package core.command;

import core.Service;
import service.guild.core.GuildCommandRouter;
import service.guild.core.GuildManager;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandRouter {
    private final Logger logger = LoggerFactory.getLogger(CommandRouter.class);

    public CommandRouter() {}

    public void parseCommand(MessageReceivedEvent e){
        Guild guild = e.getGuild();
        String guildId = guild.getId();

        if(!Service.guildManagers.containsKey(guildId)) {
            GuildManager newGuildManager = new GuildManager(guild);
            Service.guildManagers.put(guildId, newGuildManager);
        }

        GuildManager guildManager = Service.guildManagers.get(guildId);
        GuildCommandRouter guildCommandRouter = guildManager.getGuildCommandRouter();
        guildCommandRouter.route(e, guildManager);
    }
}