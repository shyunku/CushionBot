package service.music.Core;

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

import java.util.concurrent.TimeUnit;

public class MusicStreamer {
    private final Logger logger = LoggerFactory.getLogger(MusicStreamer.class);
    public static final int MAX_VOLUME = 200;

    private final TextChannel musicChannel;
    private final TrackScheduler scheduler;
    private final Link link;
    private final LavalinkPlayer player;
    private final MusicBoxUpdateHandler musicBoxUpdateHandler;
    private final long guildId;
    private volatile long volume = 100;
    private volatile boolean paused = false;

    public MusicStreamer(TextChannel textChannel, Link link, MusicBoxUpdateHandler musicBoxUpdateHandler) {
        this.musicChannel = textChannel;
        this.link = link;
        this.guildId = textChannel.getGuild().getIdLong();
        this.player = link.createOrUpdatePlayer().block();
        this.musicBoxUpdateHandler = musicBoxUpdateHandler;
        this.scheduler = new TrackScheduler(this.player, musicBoxUpdateHandler);
        MusicEvents.register(guildId, this);
    }

    void onTrackStart(TrackStartEvent e) {
        this.scheduler.onTrackStart(e);
    }

    void onTrackEnd(TrackEndEvent e) {
        this.scheduler.onTrackEnd(e);
    }

    void onTrackException(TrackExceptionEvent e) {
        this.scheduler.onTrackException(e);
    }

    void onTrackStuck(TrackStuckEvent e) {
        this.scheduler.onTrackStuck(e);
    }

    /** The Lavalink node restarted and lost this guild's player. */
    void onNodeLost() {
        scheduler.reset();
        paused = false;
        musicBoxUpdateHandler.onActionEnd();
    }

    private void load(Member requester, String identifier) {
        LavaAudioLoader loader = new LavaAudioLoader(requester, musicChannel, scheduler);
        this.link.loadItem(identifier).subscribe(loader, error -> {
            logger.error("Could not load {}", identifier, error);
            notice("음악 서버에 연결할 수 없어 곡을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
        });
    }

    private void notice(String message) {
        musicChannel.sendMessage(message).queue(sent -> sent.delete().queueAfter(10, TimeUnit.SECONDS), failure -> {
        });
    }

    public void addTrackByUrl(Member requester, String url) {
        load(requester, url);
    }

    public void addPlaylistByUrl(Member requester, String url) {
        load(requester, url);
    }

    public void addTrackByQuery(Member requester, String query) {
        load(requester, "ytsearch:" + query);
    }

    public void shuffleTracksOnQueue() {
        scheduler.shuffleTracks();
    }

    /** Stop and empty the queue; a paused player is unpaused so the next track is audible. */
    public void clearTracksOfQueue() {
        scheduler.clearTracks();
        if (paused) setPaused(false);
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

    /** Volume 0..{@value #MAX_VOLUME}; out-of-range values are clamped. */
    public int setVolume(int volume) {
        int clamped = Math.max(0, Math.min(MAX_VOLUME, volume));
        player.setVolume(clamped).block();
        this.volume = clamped;
        return clamped;
    }

    public void destroy() {
        MusicEvents.unregister(guildId, this);
        scheduler.reset();
        try {
            this.link.destroy().block();
        } catch (RuntimeException e) {
            logger.warn("Could not destroy Lavalink link for guild {}: {}", guildId, e.getMessage());
        }
    }
}
