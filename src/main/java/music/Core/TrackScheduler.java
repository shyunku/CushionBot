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
//        boolean wasEmpty = trackQueue.isEmpty();
//        trackQueue.offer(audioTrack);
//
//        if(wasEmpty){
//            audioPlayer.startTrack(audioTrack, true);
//        }
    }

    public void nextTrack(){
        audioPlayer.startTrack(trackQueue.poll(), false);
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
                    nextTrack();
                    addTrackToQueue(clone);
                    break;
                case REPEAT_SINGLE:
                    audioPlayer.startTrack(getCurrentTrack().makeClone(), false);
                    break;
            }
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        super.onTrackException(player, track, exception);
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        super.onTrackStuck(player, track, thresholdMs);
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
