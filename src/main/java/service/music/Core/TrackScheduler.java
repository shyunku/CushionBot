package service.music.Core;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.music.object.MusicPlayMode;
import service.music.object.MusicTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;

public class TrackScheduler extends AudioEventAdapter {
    private final Logger logger = LoggerFactory.getLogger(TrackScheduler.class);
    private AudioPlayer audioPlayer;
    private MusicTrack currentTrack;
    private final Queue<MusicTrack> trackQueue = new LinkedList<>();

    private MusicPlayMode musicPlayMode = MusicPlayMode.NORMAL;

    private MusicBoxUpdateHandler musicBoxUpdateHandler;


    public TrackScheduler(AudioPlayer audioPlayer, MusicBoxUpdateHandler musicTrackEndHandler) {
        this.audioPlayer = audioPlayer;
        this.musicBoxUpdateHandler = musicTrackEndHandler;
    }

    public void playTrack(MusicTrack musicTrack, boolean allowInterrupt) {
        boolean started = audioPlayer.startTrack(musicTrack.audioTrack, !allowInterrupt);
        if (!started) {
            trackQueue.offer(musicTrack);
        } else {
            this.currentTrack = musicTrack;
        }
    }

    public void addMusicTrack(MusicTrack musicTrack) {
        this.playTrack(musicTrack, false);
    }

    public void nextTrack() {
        if (musicPlayMode == MusicPlayMode.REPEAT_ALL) {
            trackQueue.offer(currentTrack.makeClone());
        }
        if (trackQueue.isEmpty()) {
            this.currentTrack = null;
            return;
        }
        this.playTrack(trackQueue.poll(), true);
    }

    public void skipUntilTrack(String trackId) {
        MusicTrack polled = null;
        while (!trackQueue.isEmpty()) {
            polled = trackQueue.poll();
            if (polled.trackInfo.getId().equals(trackId)) break;
        }
        if (polled != null) {
            this.playTrack(polled, true);
        } else {
            logger.error("Track not found");
        }
    }

    public void clearTracks() {
        trackQueue.clear();
        audioPlayer.stopTrack();
    }

    public void shuffleTracks() {
        if (trackQueue.isEmpty()) return;
        ArrayList<MusicTrack> tracks = new ArrayList<>(trackQueue);
        Collections.shuffle(tracks);
        trackQueue.clear();
        trackQueue.addAll(tracks);
    }

    public MusicTrack getMusicTrackByAudioTrack(AudioTrack audioTrack) {
        for (MusicTrack musicTrack : trackQueue) {
            if (musicTrack.audioTrack.getIdentifier().equals(audioTrack.getIdentifier())) {
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

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        super.onTrackStart(player, track);
        logger.debug(String.format("Track Start: %s", track.getInfo().title));
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        super.onTrackEnd(player, track, endReason);
        logger.debug(String.format("Track End: %s", track.getInfo().title));

        if (endReason.mayStartNext) {
            switch (musicPlayMode) {
                case NORMAL:
                    nextTrack();
                    break;
                case REPEAT_ALL:
                    MusicTrack clone = getCurrentTrack().makeClone();
                    nextTrack();
                    this.addMusicTrack(clone);
                    break;
                case REPEAT_SINGLE:
                    clone = getCurrentTrack().makeClone();
                    this.playTrack(clone, true);
                    break;
            }
        }
        musicBoxUpdateHandler.onActionEnd();
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        logger.error(String.format("Track exception occurred while playing %s", track.getInfo().title), exception);
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        logger.error(String.format("Track stuck while playing %s", track.getInfo().title));
    }

    public MusicBoxUpdateHandler getMusicBoxUpdateHandler() {
        return musicBoxUpdateHandler;
    }
}
