package music.object;

public class YoutubeTrackInfo {
    private String title;
    private String id;
    private String ThumbnailURL;
    private String ChannelTitle;

    public YoutubeTrackInfo(String title, String id, String thumbnailURL, String channelTitle) {
        this.title = title;
        this.id = id;
        ThumbnailURL = thumbnailURL;
        ChannelTitle = channelTitle;
    }

    public String getTitle() {
        return title;
    }

    public String getId() {
        return id;
    }

    public String getThumbnailURL() {
        return ThumbnailURL;
    }

    public String getChannelTitle() {
        return ChannelTitle;
    }
}
