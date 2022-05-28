package core;

import Utilities.TokenManager;
import core.command.CommandRouter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;

public class InternalEventListener extends ListenerAdapter {
    private final Logger logger = LoggerFactory.getLogger(InternalEventListener.class);
    private final CommandRouter commandRouter = new CommandRouter();

    public InternalEventListener() {
    }

    @Override
    public void onReady(@Nonnull ReadyEvent e) {
        JDA jda = e.getJDA();
        List<Guild> guilds = jda.getGuilds();
        logger.info("Bot is now online! Have fun.");
        logger.info(guilds.size() + " guilds connected.");
        jda.getPresence().setActivity(Activity.playing(Version.CURRENT + "v - $help"));
        logger.info("=====================================================================");
    }

    @Override
    public void onGuildVoiceLeave(@Nonnull GuildVoiceLeaveEvent e) {
        super.onGuildVoiceLeave(e);
        AudioChannel audioChannel = e.getChannelLeft();
        Guild guild = e.getGuild();
        List<Member> participants = audioChannel.getMembers();

        if(participants.size() == 1) {
            for(Member member : participants) {
                if(member.getId().contentEquals(TokenManager.BOT_CLIENT_ID)) {
                    guild.getAudioManager().closeAudioConnection();
                }
            }
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent e){
        commandRouter.parseCommand(e);
    }
}