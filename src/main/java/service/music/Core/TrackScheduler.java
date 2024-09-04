package service.music.Core;

import dev.arbjerg.lavalink.client.event.TrackEndEvent;
import dev.arbjerg.lavalink.client.event.TrackExceptionEvent;
import dev.arbjerg.lavalink.client.event.TrackStartEvent;
import dev.arbjerg.lavalink.client.event.TrackStuckEvent;
import dev.arbjerg.lavalink.client.player.LavalinkPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.music.object.MusicPlayMode;
import service.music.object.MusicTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;

public class TrackScheduler {
    private final Logger logger = LoggerFactory.getLogger(TrackScheduler.class);
    private final LavalinkPlayer player;
    private MusicTrack currentTrack;
    private final Queue<MusicTrack> trackQueue = new LinkedList<>();
    private MusicPlayMode musicPlayMode = MusicPlayMode.NORMAL;
    private final MusicBoxUpdateHandler musicBoxUpdateHandler;

    public TrackScheduler(LavalinkPlayer player, MusicBoxUpdateHandler musicTrackEndHandler) {
        this.player = player;
        this.musicBoxUpdateHandler = musicTrackEndHandler;
    }

    private void playTrack(MusicTrack musicTrack) {
        player.setTrack(musicTrack.track).block();
        this.currentTrack = musicTrack;
    }

    public void addMusicTrack(MusicTrack musicTrack) {
        if (currentTrack == null) {
            this.playTrack(musicTrack);
        } else {
            trackQueue.offer(musicTrack);
        }
    }

    public void nextTrack() {
        if (musicPlayMode == MusicPlayMode.REPEAT_ALL) {
            trackQueue.offer(currentTrack.copy());
        }
        if (trackQueue.isEmpty()) {
            this.player.stopTrack().block();
            this.currentTrack = null;
            return;
        }
        this.playTrack(trackQueue.poll());
    }

    public void skipUntilTrack(String trackId) {
        MusicTrack polled = null;
        while (!trackQueue.isEmpty()) {
            polled = trackQueue.poll();
            if (polled.getIdentifier().equals(trackId)) break;
        }
        if (polled != null) {
            this.playTrack(polled);
        } else {
            logger.error("Track not found");
        }
    }

    public void clearTracks() {
        trackQueue.clear();
        player.stopTrack().block();
        this.currentTrack = null;
    }

    public void shuffleTracks() {
        if (trackQueue.isEmpty()) return;
        ArrayList<MusicTrack> tracks = new ArrayList<>(trackQueue);
        Collections.shuffle(tracks);
        trackQueue.clear();
        trackQueue.addAll(tracks);
    }

    public MusicTrack getMusicTrackByAudioTrack(MusicTrack track) {
        for (MusicTrack musicTrack : trackQueue) {
            if (musicTrack.getIdentifier().equals(track.getIdentifier())) {
                return musicTrack;
            }
        }
        return null;
    }

    public MusicPlayMode getNextMusicPlayMode() {
        switch (musicPlayMode) {
            case NORMAL:
                return MusicPlayMode.REPEAT_ALL;
            case REPEAT_ALL:
                return MusicPlayMode.REPEAT_SINGLE;
            case REPEAT_SINGLE:
                return MusicPlayMode.NORMAL;
        }
        return MusicPlayMode.NORMAL;
    }

    public void removeCurrentTrack() {
        if (currentTrack != null) {
            currentTrack = null;
        }
    }

    public MusicTrack getCurrentTrack() {
        return this.currentTrack;
    }

    public MusicPlayMode getMusicPlayMode() {
        return musicPlayMode;
    }

    public void setMusicPlayMode(MusicPlayMode musicPlayMode) {
        this.musicPlayMode = musicPlayMode;
    }


    public ArrayList<MusicTrack> getCurrentMusicTracks() {
        return new ArrayList<>(trackQueue);
    }

    public void onTrackStart(TrackStartEvent e) {
        logger.info(String.format("Track Start: %s", e.getTrack().getInfo().getTitle()));
    }

    public void onTrackEnd(TrackEndEvent e) {
        logger.info(String.format("Track End: %s", e.getTrack().getInfo().getTitle()));

        if (e.getEndReason().getMayStartNext()) {
            switch (musicPlayMode) {
                case NORMAL:
                    nextTrack();
                    break;
                case REPEAT_ALL:
                    MusicTrack clone = getCurrentTrack().copy();
                    nextTrack();
                    this.addMusicTrack(clone);
                    break;
                case REPEAT_SINGLE:
                    clone = getCurrentTrack().copy();
                    this.playTrack(clone);
                    break;
            }
        }
        musicBoxUpdateHandler.onActionEnd();
    }

    public void onTrackException(TrackExceptionEvent e) {
        logger.error(String.format("Track exception occurred while playing %s: %s", e.getTrack().getInfo().getTitle(), e.getException().getMessage()));
    }

    public void onTrackStuck(TrackStuckEvent e) {
        logger.error(String.format("Track stuck while playing %s", e.getTrack().getInfo().getTitle()));
    }

    public MusicBoxUpdateHandler getMusicBoxUpdateHandler() {
        return musicBoxUpdateHandler;
    }
}
