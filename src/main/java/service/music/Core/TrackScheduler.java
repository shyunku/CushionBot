package service.music.Core;

import dev.arbjerg.lavalink.client.event.TrackEndEvent;
import dev.arbjerg.lavalink.client.event.TrackExceptionEvent;
import dev.arbjerg.lavalink.client.event.TrackStartEvent;
import dev.arbjerg.lavalink.client.event.TrackStuckEvent;
import dev.arbjerg.lavalink.client.player.LavalinkPlayer;
import dev.arbjerg.lavalink.protocol.v4.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.music.object.MusicPlayMode;
import service.music.object.MusicTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;

/**
 * Queue and play mode of one guild. Load results, Lavalink events and button/command handlers arrive on
 * different threads, so every method that reads or changes the queue is synchronized.
 */
public class TrackScheduler {
    private final Logger logger = LoggerFactory.getLogger(TrackScheduler.class);
    private final LavalinkPlayer player;
    private MusicTrack currentTrack;
    private final LinkedList<MusicTrack> trackQueue = new LinkedList<>();
    private MusicPlayMode musicPlayMode = MusicPlayMode.NORMAL;
    private final MusicBoxUpdateHandler musicBoxUpdateHandler;

    public TrackScheduler(LavalinkPlayer player, MusicBoxUpdateHandler musicTrackEndHandler) {
        this.player = player;
        this.musicBoxUpdateHandler = musicTrackEndHandler;
    }

    private void playTrack(MusicTrack musicTrack) {
        // claim the slot first so a second track loaded at the same moment queues instead of replacing it
        this.currentTrack = musicTrack;
        try {
            player.setTrack(musicTrack.track).block();
        } catch (RuntimeException e) {
            this.currentTrack = null;
            throw e;
        }
    }

    public synchronized void addMusicTrack(MusicTrack musicTrack) {
        if (currentTrack == null) {
            this.playTrack(musicTrack);
        } else {
            trackQueue.offer(musicTrack);
        }
    }

    public synchronized void nextTrack() {
        if (musicPlayMode == MusicPlayMode.REPEAT_ALL && currentTrack != null) {
            trackQueue.offer(currentTrack.copy());
        }
        if (trackQueue.isEmpty()) {
            this.currentTrack = null;
            this.player.stopTrack().block();
            return;
        }
        this.playTrack(trackQueue.poll());
    }

    /** Jump to the queued track at {@code index} (0 = next), dropping the ones before it. */
    public synchronized void skipToIndex(int index) {
        if (index < 0 || index >= trackQueue.size()) {
            logger.warn("Track index {} out of range ({})", index, trackQueue.size());
            return;
        }
        for (int i = 0; i < index; i++) trackQueue.poll();
        this.playTrack(trackQueue.poll());
    }

    public synchronized void clearTracks() {
        trackQueue.clear();
        this.currentTrack = null;
        player.stopTrack().block();
    }

    /** Forget the playing track without talking to Lavalink (its player is gone after a node restart). */
    public synchronized void reset() {
        trackQueue.clear();
        this.currentTrack = null;
    }

    public synchronized void shuffleTracks() {
        if (trackQueue.isEmpty()) return;
        ArrayList<MusicTrack> tracks = new ArrayList<>(trackQueue);
        Collections.shuffle(tracks);
        trackQueue.clear();
        trackQueue.addAll(tracks);
    }

    public synchronized MusicPlayMode getNextMusicPlayMode() {
        switch (musicPlayMode) {
            case NORMAL:
                return MusicPlayMode.REPEAT_ALL;
            case REPEAT_ALL:
                return MusicPlayMode.REPEAT_SINGLE;
            default:
                return MusicPlayMode.NORMAL;
        }
    }

    public synchronized MusicTrack getCurrentTrack() {
        return this.currentTrack;
    }

    public synchronized MusicPlayMode getMusicPlayMode() {
        return musicPlayMode;
    }

    public synchronized void setMusicPlayMode(MusicPlayMode musicPlayMode) {
        this.musicPlayMode = musicPlayMode;
    }

    public synchronized ArrayList<MusicTrack> getCurrentMusicTracks() {
        return new ArrayList<>(trackQueue);
    }

    public void onTrackStart(TrackStartEvent e) {
        logger.info("Track Start: {}", e.getTrack().getInfo().getTitle());
    }

    public void onTrackEnd(TrackEndEvent e) {
        logger.info("Track End: {} ({})", e.getTrack().getInfo().getTitle(), e.getEndReason());
        synchronized (this) {
            if (e.getEndReason().getMayStartNext()) {
                if (musicPlayMode == MusicPlayMode.REPEAT_SINGLE && currentTrack != null
                        && e.getEndReason() != Message.EmittedEvent.TrackEndEvent.AudioTrackEndReason.LOAD_FAILED) {
                    this.playTrack(currentTrack.copy());
                } else {
                    // NORMAL and REPEAT_ALL (nextTrack re-queues the finished track); a track that failed to
                    // load is skipped even in single repeat instead of being retried forever
                    nextTrack();
                }
            }
        }
        musicBoxUpdateHandler.onActionEnd();
    }

    public void onTrackException(TrackExceptionEvent e) {
        logger.error("Track exception occurred while playing {}: {}", e.getTrack().getInfo().getTitle(), e.getException().getMessage());
    }

    public void onTrackStuck(TrackStuckEvent e) {
        logger.error("Track stuck while playing {}", e.getTrack().getInfo().getTitle());
    }

    public MusicBoxUpdateHandler getMusicBoxUpdateHandler() {
        return musicBoxUpdateHandler;
    }
}
