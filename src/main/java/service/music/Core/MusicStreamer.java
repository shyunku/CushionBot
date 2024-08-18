package service.music.Core;

import dev.arbjerg.lavalink.client.LavalinkNode;
import dev.arbjerg.lavalink.client.Link;
import dev.arbjerg.lavalink.client.event.TrackEndEvent;
import dev.arbjerg.lavalink.client.event.TrackExceptionEvent;
import dev.arbjerg.lavalink.client.event.TrackStartEvent;
import dev.arbjerg.lavalink.client.event.TrackStuckEvent;
import dev.arbjerg.lavalink.client.player.LavalinkPlayer;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.music.object.MusicPlayMode;

public class MusicStreamer {
    private final Logger logger = LoggerFactory.getLogger(MusicStreamer.class);

    private final TextChannel musicChannel;
    private final TrackScheduler scheduler;
    private final Link link;
    private final LavalinkPlayer player;
    private final MusicBoxUpdateHandler musicBoxUpdateHandler;
    private long volume = 100;
    private boolean paused = false;

    public MusicStreamer(TextChannel textChannel, Link link, MusicBoxUpdateHandler musicBoxUpdateHandler) {
        this.musicChannel = textChannel;
        this.link = link;
        this.player = link.createOrUpdatePlayer().block();
        this.musicBoxUpdateHandler = musicBoxUpdateHandler;
        this.scheduler = new TrackScheduler(this.player, musicBoxUpdateHandler);

        LavalinkNode node = link.getNode();
        node.on(TrackStartEvent.class).subscribe(scheduler::onTrackStart);
        node.on(TrackEndEvent.class).subscribe(scheduler::onTrackEnd);
        node.on(TrackExceptionEvent.class).subscribe(scheduler::onTrackException);
        node.on(TrackStuckEvent.class).subscribe(scheduler::onTrackStuck);
    }

    public void addTrackByUrl(Member requester, String url) {
        LavaAudioLoader loader = new LavaAudioLoader(requester, musicChannel, scheduler);
        this.link.loadItem(url).subscribe(loader);
    }

    public void addPlaylistByUrl(Member requester, String url) {
        LavaAudioLoader loader = new LavaAudioLoader(requester, musicChannel, scheduler);
        this.link.loadItem(url).subscribe(loader);
    }

    public void addTrackByQuery(Member requester, String query) {
        LavaAudioLoader loader = new LavaAudioLoader(requester, musicChannel, scheduler);
        this.link.loadItem("ytsearch:" + query).subscribe(loader);
    }

    public void shuffleTracksOnQueue() {
        scheduler.shuffleTracks();
    }

    public void clearTracksOfQueue() {
        scheduler.clearTracks();
    }

    public void skipCurrentTracksOfQueue() {
        scheduler.nextTrack();
    }

    public void repeatTrackToQueue(MusicPlayMode musicPlayMode) {
        scheduler.setMusicPlayMode(musicPlayMode);
    }

    public TrackScheduler getScheduler() {
        return scheduler;
    }

    public TextChannel getMusicChannel() {
        return musicChannel;
    }

    public boolean isPaused() {
        return this.paused;
    }

    public void setPaused(boolean paused) {
        player.setPaused(paused).block();
        this.paused = paused;
    }

    public long getVolume() {
        return this.volume;
    }

    public void setVolume(int volume) {
        player.setVolume(volume).block();
        this.volume = volume;
    }

    public void destroy() {
        this.link.destroy().block();
    }
}
