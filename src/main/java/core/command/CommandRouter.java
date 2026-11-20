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
        // DMs have no guild; only guilds set up at startup (or joined later) are handled here
        if (!e.isFromGuild()) return;
        GuildManager guildManager = Service.guildManagers.get(e.getGuild().getId());
        if (guildManager == null) return;
        GuildCommandRouter guildCommandRouter = guildManager.getGuildCommandRouter();
        guildCommandRouter.route(e, guildManager);
    }
}