package service.music.Core;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import core.Version;
import exceptions.MusicNotFoundException;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.managers.AudioManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.discord.ControlBox;
import service.discord.MessageEmbedProps;
import service.guild.core.GuildUtil;
import service.inmemory.RedisClient;
import service.music.object.MusicTrack;
import service.music.object.YoutubeTrackInfo;
import service.music.tools.YoutubeCrawler;

import java.awt.*;
import java.util.ArrayList;

public class MusicBox implements ControlBox {
    private final Logger logger = LoggerFactory.getLogger(MusicBox.class);
    private AudioManager audioManager;
    private Guild guild;
    private Message musicBoxMessage;
    private MusicStreamer streamer;

    public MusicBox(Guild guild, TextChannel musicChannel) {
        this.guild = guild;
        audioManager = guild.getAudioManager();

        String chanKey = GuildUtil.musicChannelKey(guild.getId());
        if (RedisClient.has(chanKey)) {
            String chanId = RedisClient.get(chanKey);
            TextChannel channel = guild.getTextChannelById(chanId);
            if (channel != null) {
                musicChannel = channel;
            }
        }

        if (musicChannel != null) {
            String msgKey = GuildUtil.musicBoxMessageKey(guild.getId());
            if (RedisClient.has(msgKey)) {
                String msgId = RedisClient.get(msgKey);
                try {
                    Message message = musicChannel.retrieveMessageById(msgId).complete();
                    if (message != null) {
                        musicBoxMessage = message;
                    }
                } catch (ErrorResponseException e) {
                    e.printStackTrace();
                }
            }

            streamer = new MusicStreamer(musicChannel, audioManager, this::updateEmbed);

            // initial update embed
            this.updateEmbed();
        }
    }

    public void quickPlay(String searchQuery, Member requester) throws MusicNotFoundException, GoogleJsonResponseException {
        if (searchQuery.contains("www.youtube.com") || searchQuery.contains("music.youtube.com")) {
            streamer.addTrackListToQueue(requester, searchQuery);
        } else {
            ArrayList<YoutubeTrackInfo> trackCandidates = YoutubeCrawler.getVideoCandidates(searchQuery, 1, requester);
            if (trackCandidates.isEmpty()) {
                throw new MusicNotFoundException();
            }
            YoutubeTrackInfo selectedTrackInfo = trackCandidates.get(0);
            streamer.addTrackToQueue(requester, selectedTrackInfo);
        }
    }

    public MessageEmbed getInitialSettingEmbed() {
        TextChannel musicChannel = this.getMusicChannel();
        if (musicChannel == null) return null;

        MusicActionEmbedBuilder builder = new MusicActionEmbedBuilder();
        builder.setTitle(String.format(
                        "%s\"%s\" 채널이 \"%s\" 서버의 음악 채널로 지정되었습니다.",
                        Version.PRODUCTION_MODE ? "" : "[점검 모드] ",
                        musicChannel.getName(), musicChannel.getGuild().getName()))
                .setDescription("재생할 음악이 없습니다. 이 채널에 검색어를 입력하면 자동으로 재생됩니다.")
                .setColor(new Color(0, 255, 187));

        return builder.buildWithoutEmbed();
    }

    private MessageEmbedProps getCurrentMusicActionEmbed() {
        TrackScheduler trackScheduler = streamer.getTrackScheduler();
        MusicTrack currentTrack = trackScheduler.getCurrentTrack();
        ArrayList<MusicTrack> trackInfoList = trackScheduler.getCurrentMusicTracks();

        AudioChannel connectedChannel = audioManager.getConnectedChannel();
        String title = "쿠션 봇 음악 재생기";
        if (connectedChannel != null) {
            title = String.format("%s에서 스트리밍 중", connectedChannel.getName());
        }
        title = (Version.PRODUCTION_MODE ? "" : "[점검모드] ") + title;

        MusicActionEmbedBuilder builder = new MusicActionEmbedBuilder();
        builder.setTitle(title)
                .setColor(new Color(0, 255, 187))
                .setControlButtons(streamer.isPaused(), streamer.getTrackScheduler().getMusicPlayMode())
                .setTrackList(currentTrack, trackInfoList, streamer.getTrackScheduler().getMusicPlayMode(), streamer.getVolume());

        if (trackInfoList.isEmpty() && currentTrack == null) {
            builder.setDescription("재생할 음악이 없습니다. 이 채널에 검색어를 입력하면 자동으로 재생됩니다.");
        }
        return builder.build();
    }

    @Override
    public void updateEmbed() {
        MessageEmbedProps embed = getCurrentMusicActionEmbed();
        if (musicBoxMessage == null) {
            embed.sendMessageEmbedWithHook(getMusicChannel(), message -> {
                setMusicBoxMessage(message);
                this.updateNonNullMusicActionEmbed(embed);
            });
            return;
        }
        this.updateNonNullMusicActionEmbed(embed);
    }

    @Override
    public void clearEmbed() {
        if (musicBoxMessage == null) return;
        musicBoxMessage.delete().queue();
        musicBoxMessage = null;
        RedisClient.del(GuildUtil.musicBoxMessageKey(guild.getId()));
    }

    private void updateNonNullMusicActionEmbed(MessageEmbedProps embed) {
        assert musicBoxMessage != null;
        embed.editMessageEmbed(musicBoxMessage);
    }

    public MusicStreamer getStreamer() {
        return streamer;
    }

    public TextChannel getMusicChannel() {
        return streamer.getMusicChannel();
    }

    public AudioManager getAudioManager() {
        return audioManager;
    }

    public void setMusicChannel(TextChannel musicChannel) {
        if (musicChannel == null) return;
        this.streamer.setMusicChannel(musicChannel);
        RedisClient.set(GuildUtil.musicChannelKey(guild.getId()), musicChannel.getId());
    }

    public void setMusicBoxMessage(Message musicBoxMessage) {
        if (musicBoxMessage == null) return;
        this.musicBoxMessage = musicBoxMessage;
        RedisClient.set(GuildUtil.musicBoxMessageKey(guild.getId()), musicBoxMessage.getId());
    }
}
