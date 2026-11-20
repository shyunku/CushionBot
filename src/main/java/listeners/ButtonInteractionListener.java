package listeners;

import core.Service;
import exceptions.GuildManagerNotFoundException;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import service.discord.JdaUtil;
import service.leagueoflegends.Core.LolBox;
import service.music.Core.MusicBox;
import service.music.Core.MusicStreamer;
import service.music.Core.TrackScheduler;
import service.music.object.MusicPlayMode;
import service.teamgg.LanePanel;

public class ButtonInteractionListener extends ListenerAdapter {
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(ButtonInteractionListener.class);

    @Override
    public void onButtonInteraction(ButtonInteractionEvent e) {
        String componentId = e.getComponentId();
        // the team.gg lane panel is personal and does not need the guild's music/LoL setup
        if (componentId.startsWith(LanePanel.PREFIX)) {
            try {
                LanePanel.onButton(e);
            } catch (Exception exception) {
                logger.error("team.gg lane button {} failed", componentId, exception);
                if (!e.isAcknowledged()) e.reply("처리 중 오류가 발생했습니다.").setEphemeral(true).queue();
            }
            return;
        }
        Guild guild = e.getGuild();
        if (guild == null || !Service.guildManagers.containsKey(guild.getId())) {
            logger.warn("Button {} pressed where no guild manager is set.", componentId);
            e.reply("이 서버에서는 아직 사용할 수 없습니다.").setEphemeral(true).queue();
            return;
        }
        String guildId = guild.getId();

        if (componentId.startsWith("music")) {
            try {
                MusicBox musicBox = Service.GetMusicBoxByGuildId(guildId);
                MusicStreamer musicStreamer = musicBox.getStreamer();
                if (musicStreamer == null) {
                    e.reply("음악 채널이 아직 설정되지 않았습니다. /음악채널 명령어로 먼저 설정해주세요.").setEphemeral(true).queue();
                    return;
                }
                TrackScheduler trackScheduler = musicStreamer.getScheduler();

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
                e.reply("음악 채널이 아직 설정되지 않았습니다. /음악채널 명령어로 먼저 설정해주세요.").setEphemeral(true).queue();
            } catch (Exception exception) {
                logger.error("Music button {} failed", componentId, exception);
                if (!e.isAcknowledged()) e.reply("음악 서버 요청이 실패했습니다. 잠시 후 다시 시도해주세요.").setEphemeral(true).queue();
            }
        } else if (componentId.startsWith("lol")) {
            try {
                LolBox lolBox = Service.GetLolBoxByGuildId(guildId);
                String userId = e.getUser().getId();

                switch (componentId) {
                    case "lolJoin":
                        lolBox.addMemberAnswer(userId, true);
                        break;
                    case "lolNotJoin":
                        lolBox.addMemberAnswer(userId, false);
                        break;
                    case "lolDontKnow":
                        lolBox.removeMemberAnswer(userId);
                        break;
                }

                e.deferEdit().queue();
                lolBox.updateEmbed();
            } catch (GuildManagerNotFoundException exception) {
                e.reply("내전 채널이 아직 설정되지 않았습니다. /내전채널 명령어로 먼저 설정해주세요.").setEphemeral(true).queue();
            } catch (Exception exception) {
                logger.error("LoL button {} failed", componentId, exception);
                if (!e.isAcknowledged()) e.reply("처리 중 오류가 발생했습니다.").setEphemeral(true).queue();
            }
        }
    }
}
