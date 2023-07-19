package core;

import Utilities.TokenManager;
import core.command.CommandRouter;
import exceptions.MusicBoxNotFoundException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.jetbrains.annotations.NotNull;
import service.discord.JdaUtil;
import core.command.SlashCommandParser;
import service.music.Core.MusicActionEmbed;
import service.music.Core.MusicStreamer;
import service.music.Core.TrackScheduler;
import service.music.object.MusicPlayMode;
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
import service.music.Core.MusicBox;
import service.music.object.YoutubeTrackInfo;

import javax.annotation.Nonnull;
import java.util.ArrayList;
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
        for (Guild g : guilds) {
            logger.info("        --- Guild: " + g.getName() + " (" + g.getId() + ")");
        }
        jda.getPresence().setActivity(Activity.playing(Version.CURRENT + "v - $help"));
        logger.info("=====================================================================");

        for(Guild g : guilds) {
            g.updateCommands().addCommands(
                    Commands.slash("test", "test description"),
                    Commands.slash("music", "이 텍스트 채널을 음악 채널로 지정합니다.")
            ).queue();
        }

        // leave all audio channels
//        for(Guild g : guilds) {
//            g.getAudioManager().closeAudioConnection();
//        }
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
    public void onSelectMenuInteraction(@NotNull SelectMenuInteractionEvent e) {
        Guild guild = e.getGuild();
        String guildId = guild.getId();
        String componentId = e.getComponentId();

        logger.info(componentId);

        try {
            MusicBox musicBox = Service.GetMusicBoxByGuildId(guildId);
            MusicStreamer musicStreamer = musicBox.getStreamer();
            TrackScheduler trackScheduler = musicStreamer.getTrackScheduler();
            if (componentId.startsWith("track-")) {
                String trackId = componentId.replace("track-", "");
                trackScheduler.skipUntilTrack(trackId);
                musicBox.updateMusicActionEmbed();
            }
        } catch (Exception exception) {
            e.reply("음악 채널이 아직 설정되지 않았습니다. /music 명령어로 먼저 설정해주세요.").queue();
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent e) {
        Guild guild = e.getGuild();
        String guildId = guild.getId();
        String componentId = e.getComponentId();

        if(!Service.guildManagers.containsKey(guildId)) {
            logger.warn("GuildManager is not set in this guild.");
            return;
        }

        try {
            MusicBox musicBox = Service.GetMusicBoxByGuildId(guildId);
            MusicStreamer musicStreamer = musicBox.getStreamer();
            TrackScheduler trackScheduler = musicStreamer.getTrackScheduler();

            switch (componentId) {
                case "musicBoxStop":
                    musicStreamer.clearTracksOfQueue();
//                    JdaUtil.LeaveCurrentAudioChannel(guild);
                    break;
                case "musicBoxPause":
                    musicStreamer.setPaused(true);
                    break;
                case "musicBoxPlay":
                    musicStreamer.setPaused(false);
                    break;
                case "musicBoxSkip":
                    musicStreamer.skipCurrentTracksOfQueue();
                    break;
                case "musicBoxRepeat":
                    MusicPlayMode nextPlayMode = trackScheduler.getNextMusicPlayMode();
                    musicStreamer.repeatTrackToQueue(nextPlayMode);
                    break;
                case "musicBoxLeave":
                    JdaUtil.LeaveCurrentAudioChannel(guild);
                    break;
            }

            e.deferEdit().queue();
            musicBox.updateMusicActionEmbed();
        } catch (MusicBoxNotFoundException exception) {
            e.reply("음악 채널이 아직 설정되지 않았습니다. /music 명령어로 먼저 설정해주세요.").queue();
        }
    }
}