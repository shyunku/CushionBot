package music.Core;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEvent;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import music.object.MusicPlayMode;
import music.object.YoutubeTrackInfo;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.managers.AudioManager;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class TrackScheduler extends AudioEventAdapter{
    private AudioPlayer audioPlayer;
    private AudioManager audioManager;
    private TextChannel textChannel;
    private AudioTrack currentTrack;
    private final Queue<AudioTrack> trackQueue = new LinkedList<>();
    private final ArrayList<YoutubeTrackInfo> trackData = new ArrayList<>();

    private AudioCloser audioCloser;

    private MusicPlayMode musicPlayMode = MusicPlayMode.NORMAL;


    public TrackScheduler(AudioPlayer audioPlayer, AudioManager audioManager, TextChannel textChannel) {
        this.audioPlayer = audioPlayer;
        this.audioManager = audioManager;
        this.textChannel = textChannel;
    }

    public void addTrackData(YoutubeTrackInfo trackInfo){
        trackData.add(trackInfo);
    }

    public void addTrackToQueue(AudioTrack audioTrack){
        if(!audioPlayer.startTrack(audioTrack, true)){
            trackQueue.offer(audioTrack);
        }
    }

    public void nextTrack(){
        audioPlayer.startTrack(trackQueue.poll(), false);
        trackData.remove(0);
    }

    public void clearTracks(){
        trackData.clear();
        trackQueue.clear();
        audioPlayer.stopTrack();
    }

    public String getMusicPlayModeDescription(){
        switch(musicPlayMode){
            case NORMAL: return "기본";
            case REPEAT_ALL: return "전제 반복";
            case REPEAT_SINGLE: return "한 곡 반복";
        }
        return "-";
    }

    public MusicPlayMode getNextMusicPlayMode() {
        switch(musicPlayMode){
            case NORMAL: return MusicPlayMode.REPEAT_ALL;
            case REPEAT_ALL: return MusicPlayMode.REPEAT_SINGLE;
            case REPEAT_SINGLE: return MusicPlayMode.NORMAL;
        }
        return MusicPlayMode.NORMAL;
    }

    public static Button getMusicPlayModeButton(MusicPlayMode mode) {
        switch(mode) {
            case NORMAL:
                return Button.secondary("musicPlayModeNormal", "🔁");
            case REPEAT_ALL:
                return Button.primary("musicPlayModeRepeatAll", "🔁");
            case REPEAT_SINGLE:
                return Button.primary("musicPlayModeRepeatSingle", "\uD83D\uDD02");
        }
        return Button.secondary("musicPlayModeNormal", "🔁");
    }

    public AudioTrack getCurrentTrack(){
        return currentTrack;
    }

    public void setMusicPlayMode(MusicPlayMode musicPlayMode){
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
        if(endReason.mayStartNext){
            switch (musicPlayMode){
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
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        System.err.println(String.format("Track Exception: %s", exception.getMessage()));
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        System.err.println(String.format("Track Stuck: %s", track.getInfo().title));
    }

    private class AudioCloser extends Thread{
        private AudioManager audioManager;
        public AudioCloser(AudioManager manager){
            audioManager = manager;
        }

        public void run(){
            audioManager.closeAudioConnection();
        }
    }

    private void print(Object o){
        System.out.println(o);
    }
}
