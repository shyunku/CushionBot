package listeners;

import core.Service;
import exceptions.GuildManagerNotFoundException;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import service.discord.JdaUtil;
import service.leagueoflegends.Core.LolBox;
import service.music.Core.MusicBox;
import service.music.Core.MusicStreamer;
import service.music.Core.TrackScheduler;
import service.music.object.MusicPlayMode;

public class ButtonInteractionListener extends ListenerAdapter {
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(ButtonInteractionListener.class);

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
