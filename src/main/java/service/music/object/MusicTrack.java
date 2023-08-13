package service.music.object;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

public class MusicTrack {
    public AudioTrack audioTrack;
    public YoutubeTrackInfo trackInfo;

    public MusicTrack(AudioTrack audioTrack, YoutubeTrackInfo trackInfo) {
        this.audioTrack = audioTrack;
        this.trackInfo = trackInfo;
    }

    public MusicTrack makeClone() {
        return new MusicTrack(this.audioTrack.makeClone(), this.trackInfo);
    }
}
