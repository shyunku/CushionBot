package core;

import Utilities.TokenManager;
import core.command.CommandRouter;
import core.command.GuildCommandRouter;
import core.command.SlashCommandParser;
import music.Core.MusicStreamSystem;
import music.Core.MusicStreamer;
import music.Core.TrackScheduler;
import music.object.MusicPlayMode;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;

public class InternalEventListener extends ListenerAdapter {
    private final Logger logger = LoggerFactory.getLogger(InternalEventListener.class);
    private final CommandRouter commandRouter = new CommandRouter();
    private SlashCommandParser slashCommandParser = new SlashCommandParser();

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

        for(Guild g : guilds) {
            g.updateCommands().addCommands(
                    Commands.slash("test", "test description"),
                    Commands.slash("music", "이 텍스트 채널을 음악 채널로 지정합니다.")
            ).queue();
        }
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

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
        String eventName = e.getName();
        switch(eventName) {
            case "test":
                slashCommandParser.test(e);
                break;
            case "music":
                slashCommandParser.music(e);
                break;
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent e) {
        Guild guild = e.getGuild();
        String guildId = guild.getId();
        String componentId = e.getComponentId();
        if(!Service.guildMusicChannelMap.containsKey(guildId)) {
            logger.warn("Music channel is not set in this guild.");
            return;
        }
        String musicTextChannelId = Service.guildMusicChannelMap.get(guildId);
        TextChannel musicTextChannel = e.getJDA().getTextChannelById(musicTextChannelId);

        if(musicTextChannel == null) {
            logger.warn("Music channel is not set in this guild.");
            return;
        }

        if(!Service.guildCommandRouters.containsKey(guildId)) {
            logger.warn("GuildCommandRouter is not set in this guild.");
            return;
        }

        GuildCommandRouter guildCommandRouter = Service.guildCommandRouters.get(guildId);
        MusicStreamSystem musicStreamSystem = guildCommandRouter.getMusicStreamSystem();
        MusicStreamer musicStreamer = musicStreamSystem.getMusicStreamer(musicTextChannel);
        TrackScheduler trackScheduler = musicStreamer.getTrackScheduler();

        switch(componentId) {
            case "musicBoxStop":
                musicStreamSystem.clearTracksOfQueue(musicTextChannel);
                AudioChannel connectedChannel = guild.getSelfMember().getVoiceState().getChannel();
                if(connectedChannel != null){
                    guild.getAudioManager().closeAudioConnection();
                }
                e.deferEdit().queue();
                break;
            case "musicBoxPause":
                e.reply("Not supported yet").queue();
                break;
            case "musicBoxPlay":
                e.reply("Not supported yet").queue();
                break;
            case "musicBoxNext":
                musicStreamSystem.skipCurrentTracksOfQueue(musicTextChannel);
                musicStreamSystem.printMusicBox(musicTextChannel);
                e.deferEdit().queue();
                break;
            case "musicBoxRepeat":
                MusicPlayMode nextPlayMode = trackScheduler.getNextMusicPlayMode();
                musicStreamSystem.repeatTrackToQueue(musicTextChannel, nextPlayMode);
                musicStreamSystem.printMusicBox(musicTextChannel);
                e.deferEdit().queue();
                break;
        }
    }
}