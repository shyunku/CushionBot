package core;

import Utilities.TokenManager;
import core.command.CommandRouter;
import exceptions.GuildManagerNotFoundException;
import listeners.ButtonInteractionListener;
import listeners.ModalInteractionListener;
import listeners.SlashCommandInteractionListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericSelectMenuInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.managers.Presence;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.discord.JdaUtil;
import service.music.Core.MusicBox;
import service.music.Core.MusicStreamer;
import service.music.Core.TrackScheduler;
import service.watcher.AccessType;
import service.watcher.GuildWatcher;

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
            setupGuild(g);
        }

        GuildWatcher.initialize();

        // leave all audio channels
//        for(Guild g : guilds) {
//            g.getAudioManager().closeAudioConnection();
//        }

        // set up listeners
        jda.addEventListener(new AutoCompleteListener());
        jda.addEventListener(new ModalInteractionListener());
        jda.addEventListener(new ButtonInteractionListener());
        jda.addEventListener(new SlashCommandInteractionListener());

        this.logger.info("All setup completed. Ready to go!");
    }

    private void setupGuild(Guild guild) {
        if (Version.PRODUCTION_MODE || guild.getId().equals("979067006223536239")) {
            Service.addGuildManagerIfNotExists(guild);
        }
        guild.updateCommands().addCommands(
                Commands.slash("test", "test description"),
                Commands.slash("clear", "최근 메시지들을 삭제합니다.")
                        .addOption(OptionType.INTEGER, "message_count", "삭제할 메시지 수를 입력하세요. (1~300)", true),
                Commands.slash("점검완료", "점검 완료 후 봇을 정상 운영합니다."),
                Commands.slash("서버랭킹", "서버 참여 랭킹을 출력합니다."),
                Commands.slash("내랭킹", "내 랭킹을 출력합니다."),
                Commands.slash("음악채널", "이 텍스트 채널을 음악 채널로 지정합니다."),
                Commands.slash("음악셔플", "재생목록을 셔플합니다."),
                Commands.slash("음악볼륨", "볼륨을 조절합니다.")
                        .addOption(OptionType.INTEGER, "볼륨", "볼륨을 입력하세요. (0~100)", true),

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
                Commands.slash("내전취소", "내전을 취소합니다."),

                Commands.slash("구인채널생성", "구인 채널을 생성합니다.")
                        .addOption(OptionType.CHANNEL, "채널", "(이미 있는 경우) 구인 채널을 입력하세요.", false),
                Commands.slash("구인", "게임이나 활동을 함께할 사람들을 구합니다."),
                Commands.slash("구인취소", "본인이 업로드한 모든 구인을 취소하거나 특정 구인을 취소합니다.")
                        .addOption(OptionType.STRING, "구인코드", "취소할 구인의 코드를 입력하세요.", false),
                Commands.slash("구인수정", "본인이 업로드한 특정 구인의 모집 시간을 변경합니다.")
                        .addOption(OptionType.STRING, "구인코드", "변경할 구인의 코드를 입력하세요.", true)
                        .addOption(OptionType.STRING, "시간", "변경할 모집 시간을 형식에 맞게 입력하세요. (ex. 23:30)", false)
                        .addOption(OptionType.INTEGER, "인원", "변경할 모집 인원을 입력하세요. (2~)", false),
                Commands.slash("구인광고", "현재 채널에 특정 구인을 광고합니다.")
                        .addOption(OptionType.STRING, "구인코드", "광고할 구인의 코드를 입력하세요.", true),

                Commands.slash("팀지지등록", "team.gg에 자신의 소환사를 연동합니다. (최초 한번만 하면 되고, 닉네임이 변경되어도 추적됩니다.)")
                        .addOption(OptionType.STRING, "소환사명", "team.gg에 등록할 소환사명을 입력하세요. (태그 제외)", true)
                        .addOption(OptionType.STRING, "태그", "소환사 태그를 입력하세요.", true),
                Commands.slash("팀지지라인", "team.gg에 등록된 소환사의 라인 선호도를 설정합니다.")
                        .addOption(OptionType.STRING, "선호도", "라인별 선호도를 탑, 정글, 미드, 원딜, 서폿 순으로 -1,0,1,2 중에 입력하세요. -1은 가기 싫은 라인, 2는 자신있는/좋아하는 라인입니다. (ex. 1,-1,2,0,2)", true)
        ).queue();

        this.logger.info("Guild {} setup completed.", guild.getName());
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        super.onGuildJoin(event);

        Guild guild = event.getGuild();
        setupGuild(guild);
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        super.onGuildLeave(event);

        this.logger.info("Guild {} left.", event.getGuild().getName());
    }

    @Override
    public void onGuildVoiceUpdate(@Nonnull GuildVoiceUpdateEvent e) {
        super.onGuildVoiceUpdate(e);
        Guild guild = e.getGuild();

        AccessType accessType = getAccessType(e);
        if (accessType != AccessType.UNKNOWN) {
            AudioChannelUnion voiceChannel = e.getChannelJoined();
            if (voiceChannel == null) voiceChannel = e.getChannelLeft();
            if (voiceChannel != null) {
                GuildWatcher.addAccessLog(accessType, guild.getId(), e.getMember().getId(), voiceChannel.getId());
            }
        }

        AudioChannel audioChannel = e.getChannelLeft();
        if (audioChannel == null) return;
        List<Member> participants = audioChannel.getMembers();

        int leftParticipants = 0;
        for (Member member : participants) {
            if (member.getId().contentEquals(TokenManager.BOT_CLIENT_ID)) continue;
            if (member.getUser().isBot()) continue;
            if (member.getId().equals(e.getMember().getId())) continue;
            leftParticipants++;
        }

        if (leftParticipants == 0) {
            try {
                this.logger.info("All participants left the voice channel. Clearing the queue.");
                MusicBox musicBox = Service.GetMusicBoxByGuildId(guild.getId());
                MusicStreamer streamer = musicBox.getStreamer();

                GuildVoiceState voiceState = guild.getSelfMember().getVoiceState();
                if (voiceState != null) {
                    AudioChannel connectedChannel = voiceState.getChannel();
                    if (connectedChannel != null && audioChannel.getId().equals(connectedChannel.getId())) {
                        JdaUtil.LeaveCurrentAudioChannel(guild);
                        streamer.clearTracksOfQueue();
                        musicBox.updateEmbed();
                        musicBox.getMusicChannel().sendMessage("모든 참가자가 음성 채널을 나갔습니다. 음악 재생을 종료합니다.").queue(sentMessage -> {
                            sentMessage.delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS);
                        });
                    }
                }
            } catch (GuildManagerNotFoundException ex) {
                logger.error("GuildManagerNotFoundException occurred while trying to get MusicBox.");
                guild.getAudioManager().closeAudioConnection();
            }
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent e) {
        commandRouter.parseCommand(e);
    }


    @Override
    public void onGenericSelectMenuInteraction(@NotNull GenericSelectMenuInteractionEvent e) {
        Guild guild = e.getGuild();
        String guildId = guild.getId();
        if (e.getValues().isEmpty()) return;
        String value = (String) e.getValues().get(0);

        try {
            MusicBox musicBox = Service.GetMusicBoxByGuildId(guildId);
            MusicStreamer musicStreamer = musicBox.getStreamer();
            TrackScheduler trackScheduler = musicStreamer.getScheduler();
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

    private static @NotNull AccessType getAccessType(@NotNull GuildVoiceUpdateEvent e) {
        AccessType accessType = AccessType.UNKNOWN;
        AudioChannelUnion joinedChannel = e.getChannelJoined();
        AudioChannelUnion leftChannel = e.getChannelLeft();
        if (joinedChannel != null && leftChannel != null) {
            // the member moved from one audio channel to another
            accessType = AccessType.MOVED;
        } else if (joinedChannel != null) {
            // the member joined an audio channel
            accessType = AccessType.JOIN;
        } else if (leftChannel != null) {
            // the member left an audio channel
            accessType = AccessType.LEAVE;
        }
        return accessType;
    }
}