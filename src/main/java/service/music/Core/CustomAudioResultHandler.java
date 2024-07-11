package service.music.Core;

import Utilities.TextStyler;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.music.object.MusicTrack;
import service.music.object.YoutubeTrackInfo;
import service.music.tools.MusicUtil;

import java.time.Duration;
import java.util.List;

public class CustomAudioResultHandler implements AudioLoadResultHandler {
    private final Logger logger = LoggerFactory.getLogger(CustomAudioResultHandler.class);
    private final Member requester;
    private final TextChannel musicChannel;
    private final TrackScheduler trackScheduler;

    private final YoutubeTrackInfo trackInfo;

    // single track requested
    public CustomAudioResultHandler(Member requester, TextChannel musicChannel, TrackScheduler trackScheduler, YoutubeTrackInfo trackInfo) {
        this.requester = requester;
        this.musicChannel = musicChannel;
        this.trackScheduler = trackScheduler;
        this.trackInfo = trackInfo;
    }

    // multiple tracks requested
    public CustomAudioResultHandler(Member requester, TextChannel musicChannel, TrackScheduler trackScheduler) {
        this.requester = requester;
        this.musicChannel = musicChannel;
        this.trackScheduler = trackScheduler;
        this.trackInfo = null;
    }

    @Override
    public void trackLoaded(AudioTrack track) {
        trackScheduler.addMusicTrack(new MusicTrack(track, trackInfo));

        StringBuilder message = new StringBuilder();
        message.append("플레이리스트에 추가되었습니다: ");
        this.logger.debug("Track loaded: {}", track.getInfo().title);

        // long to duration
        Duration duration = Duration.ofMillis(track.getDuration());
        message.append(TextStyler.Bold(track.getInfo().title));
        message.append(String.format(" (%s)", MusicUtil.getDurationString(duration)));

        musicChannel.sendMessage(message.toString()).queue(sentMessage -> {
            sentMessage.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS);
        });

        this.trackScheduler.getMusicBoxUpdateHandler().onActionEnd();
    }

    @Override
    public void playlistLoaded(AudioPlaylist playlist) {
        List<AudioTrack> trackList = playlist.getTracks();
        for (AudioTrack track : trackList) {
            String thumbnailUrl = "https://img.youtube.com/vi/" + track.getIdentifier() + "/hqdefault.jpg";
            YoutubeTrackInfo trackInfo = new YoutubeTrackInfo(
                    track.getInfo().title,
                    track.getInfo().identifier,
                    thumbnailUrl,
                    track.getInfo().author,
                    Duration.ofMillis(track.getDuration()),
                    requester
            );
            trackScheduler.addMusicTrack(new MusicTrack(track, trackInfo));
        }

        musicChannel.sendMessage("플레이리스트 " +
                TextStyler.Bold(playlist.getName()) + " : " + trackList.size() + "개의 트랙이 로드되었습니다.").queue(sentMessage -> {
            sentMessage.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS);
        });
        this.logger.debug("Playlist loaded: {}", playlist.getName());

        this.trackScheduler.getMusicBoxUpdateHandler().onActionEnd();
    }

    @Override
    public void noMatches() {
        musicChannel.sendMessage("매치되는 결과가 없습니다.").queue(message -> {
            message.delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS);
        });
        this.logger.debug("No matches found.");
    }

    @Override
    public void loadFailed(FriendlyException exception) {
        this.logger.error("Load failed", exception);
    }
}
