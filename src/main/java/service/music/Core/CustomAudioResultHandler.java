package service.music.Core;

import Utilities.TextStyler;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import service.music.object.MusicTrack;
import service.music.object.YoutubeTrackInfo;
import service.music.tools.MusicUtil;

import java.time.Duration;
import java.util.List;

public class CustomAudioResultHandler implements AudioLoadResultHandler {
    private final Member requester;
    private final TextChannel musicChannel;
    private final TrackScheduler trackScheduler;

    private final YoutubeTrackInfo trackInfo;
    private final boolean isPlaylist;

    // single track requested
    public CustomAudioResultHandler(Member requester, TextChannel musicChannel, TrackScheduler trackScheduler, YoutubeTrackInfo trackInfo) {
        this.requester = requester;
        this.musicChannel = musicChannel;
        this.trackScheduler = trackScheduler;
        this.trackInfo = trackInfo;
        this.isPlaylist = false;
    }

    // multiple tracks requested
    public CustomAudioResultHandler(Member requester, TextChannel musicChannel, TrackScheduler trackScheduler) {
        this.requester = requester;
        this.musicChannel = musicChannel;
        this.trackScheduler = trackScheduler;
        this.trackInfo = null;
        this.isPlaylist = true;
    }

    @Override
    public void trackLoaded(AudioTrack track) {
        if (isPlaylist) return;
        trackScheduler.addMusicTrack(new MusicTrack(track, trackInfo));

        StringBuilder message = new StringBuilder();
        message.append("플레이리스트에 추가되었습니다: ");

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
        if (!isPlaylist) return;
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

        this.trackScheduler.getMusicBoxUpdateHandler().onActionEnd();
    }

    @Override
    public void noMatches() {
        musicChannel.sendMessage("매치되는 결과가 없습니다.").queue();
        System.err.println("noMatches");
    }

    @Override
    public void loadFailed(FriendlyException exception) {
        musicChannel.sendMessage("load failed: " + exception.getMessage()).queue();
        exception.printStackTrace();
    }
}
