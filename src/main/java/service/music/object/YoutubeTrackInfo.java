package service.music.object;

import Utilities.Util;
import net.dv8tion.jda.api.entities.Member;
import service.music.tools.MusicUtil;

import java.time.Duration;

public class YoutubeTrackInfo {
    private String title;
    private String id;
    private String thumbnailURL;
    private String channelTitle;
    private Duration duration;
    private Member requester;

    public YoutubeTrackInfo(String title, String id, String thumbnailURL, String channelTitle, Duration duration, Member requester) {
        this.title = title;
        this.id = id;
        this.thumbnailURL = thumbnailURL;
        this.channelTitle = channelTitle;
        this.duration = duration;
        this.requester = requester;
    }

    public String getTitle() {
        return Util.unescapeHTML(title);
    }

    public String getId() {
        return id;
    }

    public String getThumbnailURL() {
        return thumbnailURL;
    }

    public String getChannelTitle() {
        return channelTitle;
    }

    public Duration getDuration() {
        return duration;
    }

    public String getDurationString() {
        return MusicUtil.getDurationString(duration);
    }

    public String getVideoUrl() {
        return "https://www.youtube.com/watch?v=" + id;
    }

    public Member getRequester() {
        return requester;
    }
}
