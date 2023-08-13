package service.music.Core;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import service.music.object.MusicPlayMode;
import service.music.object.YoutubeTrackInfo;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class TrackScheduler extends AudioEventAdapter {
    private AudioPlayer audioPlayer;
    private AudioTrack currentTrack;
    private final Queue<AudioTrack> trackQueue = new LinkedList<>();
    private final ArrayList<YoutubeTrackInfo> trackData = new ArrayList<>();

    private MusicPlayMode musicPlayMode = MusicPlayMode.NORMAL;

    private MusicBoxUpdateHandler musicBoxUpdateHandler;


    public TrackScheduler(AudioPlayer audioPlayer, MusicBoxUpdateHandler musicTrackEndHandler) {
        this.audioPlayer = audioPlayer;
        this.musicBoxUpdateHandler = musicTrackEndHandler;
    }

    public void addTrackData(YoutubeTrackInfo trackInfo) {
        trackData.add(trackInfo);

    }

    public void addTrackToQueue(AudioTrack audioTrack) {
        if (!audioPlayer.startTrack(audioTrack, true)) {
            trackQueue.offer(audioTrack);
        }
    }

    public void nextTrack() {
        if (musicPlayMode == MusicPlayMode.REPEAT_ALL) {
            trackQueue.offer(currentTrack.makeClone());
            trackData.add(trackData.get(0));
        }
        audioPlayer.startTrack(trackQueue.poll(), false);
        trackData.remove(0);
    }

    public void skipUntilTrack(String trackId) {
        if (!this.hasTrack(trackId)) return;
        AudioTrack polled = null;
        while (!trackData.get(0).getId().equals(trackId)) {
            trackData.remove(0);
            polled = trackQueue.poll();
        }
        audioPlayer.startTrack(polled, false);
    }

    public boolean hasTrack(String trackId) {
        for (YoutubeTrackInfo trackInfo : trackData) {
            if (trackInfo.getId().equals(trackId)) {
                return true;
            }
        }
        return false;
    }

    public void clearTracks() {
        trackData.clear();
        trackQueue.clear();
        audioPlayer.stopTrack();
    }

    public void shuffleTracks() {
        if (trackQueue.isEmpty()) return;

        ArrayList<YoutubeTrackInfo> newTrackData = new ArrayList<>();
        Queue<AudioTrack> newTrackQueue = new LinkedList<>();
        while (!trackData.isEmpty()) {
            int randomIndex = (int) (Math.random() * trackData.size());
            newTrackData.add(trackData.get(randomIndex));
            newTrackQueue.offer(trackQueue.poll());
        }
        trackData.addAll(newTrackData);
        trackQueue.addAll(newTrackQueue);
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

    public AudioTrack getCurrentTrack() {
        return currentTrack;
    }

    public MusicPlayMode getMusicPlayMode() {
        return musicPlayMode;
    }

    public void setMusicPlayMode(MusicPlayMode musicPlayMode) {
        this.musicPlayMode = musicPlayMode;
    }

    public ArrayList<YoutubeTrackInfo> getTrackDataList() {
        return trackData;
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        super.onTrackStart(player, track);
        currentTrack = track;
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        super.onTrackEnd(player, track, endReason);
        if (endReason.mayStartNext) {
            switch (musicPlayMode) {
                case NORMAL:
                    nextTrack();
                    break;
                case REPEAT_ALL:
                    AudioTrack clone = getCurrentTrack().makeClone();
                    YoutubeTrackInfo trackInfo = trackData.get(0);
                    nextTrack();
                    addTrackToQueue(clone);
                    addTrackData(trackInfo);
                    break;
                case REPEAT_SINGLE:
                    audioPlayer.startTrack(getCurrentTrack().makeClone(), false);
                    break;
            }
        }
        musicBoxUpdateHandler.onActionEnd();
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        System.err.println(String.format("Track Exception: %s", exception.getMessage()));
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        System.err.println(String.format("Track Stuck: %s", track.getInfo().title));
    }

    public MusicBoxUpdateHandler getMusicBoxUpdateHandler() {
        return musicBoxUpdateHandler;
    }
}
