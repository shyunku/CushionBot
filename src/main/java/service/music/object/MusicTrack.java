package service.music.object;

import dev.arbjerg.lavalink.client.player.Track;
import net.dv8tion.jda.api.entities.Member;
import service.music.tools.MusicUtil;

import java.time.Duration;

public class MusicTrack {
    public Member requester;
    public Track track;

    public MusicTrack(Member requester, Track track) {
        this.requester = requester;
        this.track = track;
    }

    public String getTitle() {
        return this.track.getInfo().getTitle();
    }

    public String getIdentifier() {
        return this.track.getInfo().getIdentifier();
    }

    public String getChannelTitle() {
        return this.track.getInfo().getAuthor();
    }

    public String getThumbnailURL() {
        return "https://img.youtube.com/vi/" + this.getIdentifier() + "/hqdefault.jpg";
    }

    public String getVideoURL() {
        return "https://www.youtube.com/watch?v=" + this.getIdentifier();
    }

    public String getDurationString() {
        long length = this.track.getInfo().getLength();
        Duration duration = Duration.ofMillis(length);
        return MusicUtil.getDurationString(duration);
    }

    public MusicTrack copy() {
        return new MusicTrack(this.requester, this.track);
    }
}
