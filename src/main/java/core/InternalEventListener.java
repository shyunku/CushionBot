package core;

import Utilities.TokenManager;
import core.command.CommandRouter;
import core.command.SlashCommandParser;
import exceptions.GuildManagerNotFoundException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.managers.Presence;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.discord.JdaUtil;
import service.leagueoflegends.Core.LolBox;
import service.music.Core.MusicBox;
import service.music.Core.MusicStreamer;
import service.music.Core.TrackScheduler;
import service.music.object.MusicPlayMode;

import javax.annotation.Nonnull;
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

        Presence presence = jda.getPresence();

        if (Version.PRODUCTION_MODE) {
            presence.setActivity(Activity.playing(Version.CURRENT + "v - 정상 운영"));
            presence.setStatus(OnlineStatus.ONLINE);
        } else {
            presence.setActivity(Activity.playing(Version.CURRENT + "v - 점검"));
            presence.setStatus(OnlineStatus.DO_NOT_DISTURB);
        }

        logger.info("=====================================================================");

        for (Guild g : guilds) {
            if (Version.PRODUCTION_MODE || g.getId().equals("979067006223536239"))
                Service.addGuildManagerIfNotExists(g);
            g.updateCommands().addCommands(
                    Commands.slash("test", "test description"),
                    Commands.slash("음악채널", "이 텍스트 채널을 음악 채널로 지정합니다."),
                    Commands.slash("내전채널", "이 텍스트 채널을 내전 채널로 지정합니다."),
                    Commands.slash("내전모으기", "내전 일정을 생성합니다.")
                            .addOption(OptionType.INTEGER, "시간", "모집 시간을 입력하세요. (0~47) 24 이상은 내일을 나타냅니다.", true)
                            .addOption(OptionType.INTEGER, "분", "모집 시간을 입력하세요. (0~59)", true),
                    Commands.slash("내전시간변경", "내전 일정을 변경합니다.")
                            .addOption(OptionType.INTEGER, "시간", "모집 시간을 입력하세요. (0~47) 24 이상은 내일을 나타냅니다.", true)
                            .addOption(OptionType.INTEGER, "분", "모집 시간을 입력하세요. (0~59)", true),
                    Commands.slash("내전정보", "내전 정보를 출력합니다."),
                    Commands.slash("내전호출", "내전 참여자 모두를 호출합니다."),
                    Commands.slash("내전종료", "내전 인원 모집을 종료합니다."),
                    Commands.slash("내전취소", "내전을 취소합니다.")
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

        if (participants.size() == 1) {
            for (Member member : participants) {
                if (member.getId().contentEquals(TokenManager.BOT_CLIENT_ID)) {
                    guild.getAudioManager().closeAudioConnection();
                }
            }
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent e) {
        commandRouter.parseCommand(e);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
        String eventName = e.getName();
        switch (eventName) {
            case "test":
                slashCommandParser.test(e);
                break;
            case "음악채널":
                slashCommandParser.music(e);
                break;
            case "내전채널":
                slashCommandParser.lol5vs5(e);
                break;
            case "내전모으기":
                slashCommandParser.lol5vs5StartOrStop(e, true);
                break;
            case "내전시간변경":
                slashCommandParser.lol5vs5ChangeStartTime(e);
                break;
            case "내전정보":
                slashCommandParser.lol5vs5PrintInfo(e);
                break;
            case "내전호출":
                slashCommandParser.lol5vs5Call(e);
                break;
            case "내전종료":
            case "내전취소":
                slashCommandParser.lol5vs5StartOrStop(e, false);
                break;
        }
    }

    @Override
    public void onSelectMenuInteraction(@NotNull SelectMenuInteractionEvent e) {
        Guild guild = e.getGuild();
        String guildId = guild.getId();
        if (e.getValues().isEmpty()) return;
        String value = e.getValues().get(0);

        try {
            MusicBox musicBox = Service.GetMusicBoxByGuildId(guildId);
            MusicStreamer musicStreamer = musicBox.getStreamer();
            TrackScheduler trackScheduler = musicStreamer.getTrackScheduler();
            if (value.startsWith("track-")) {
                String trackId = value.replace("track-", "");
                trackScheduler.skipUntilTrack(trackId);

                e.deferEdit().queue();
                musicBox.updateEmbed();
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

        if (!Service.guildManagers.containsKey(guildId)) {
            logger.warn("GuildManager is not set in this guild.");
            return;
        }

        if (componentId.startsWith("music")) {
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
                        musicStreamer.clearTracksOfQueue();
                        JdaUtil.LeaveCurrentAudioChannel(guild);
                        break;
                }

                e.deferEdit().queue();
                musicBox.updateEmbed();
            } catch (GuildManagerNotFoundException exception) {
                e.reply("음악 채널이 아직 설정되지 않았습니다. /music 명령어로 먼저 설정해주세요.").queue();
            }
        } else if (componentId.startsWith("lol")) {
            try {
                LolBox lolBox = Service.GetLolBoxByGuildId(guildId);
                Member sender = e.getMember();

                switch (componentId) {
                    case "lolJoin":
                        lolBox.addMemberAnswer(sender, true);
                        break;
                    case "lolNotJoin":
                        lolBox.addMemberAnswer(sender, false);
                        break;
                    case "lolDontKnow":
                        lolBox.removeMemberAnswer(sender);
                        break;
                }

                e.deferEdit().queue();
                lolBox.updateEmbed();
            } catch (GuildManagerNotFoundException exception) {
                e.reply("내전 채널이 아직 설정되지 않았습니다. /내전채널 명령어로 먼저 설정해주세요.").queue();
            }
        }
    }
}