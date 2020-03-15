package music.tools;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEvent;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import java.util.ArrayList;

public class TrackScheduler extends AudioEventAdapter implements AudioEventListener {
    private AudioPlayer audioPlayer;
    private ArrayList<AudioTrack> trackList;
    private boolean isPlaying = false;

    public TrackScheduler(AudioPlayer audioPlayer) {
        this.audioPlayer = audioPlayer;
        trackList = new ArrayList<>();
    }

    @Override
    public void onEvent(AudioEvent e) {
        print("무슨 이벤트가 나긴했는데 뭔지모름: "+e.toString());
    }

    //노래 큐에 추가
    public void queue(AudioTrack track){
        trackList.add(track);
        play();
    }

    public void play(){
        print("Current TrackList Size: "+trackList.size());
        for(AudioTrack track : trackList){
            audioPlayer.playTrack(track);
        }
    }

    @Override
    public void onPlayerPause(AudioPlayer player) {
        super.onPlayerPause(player);
        print("Audio player paused.");
    }

    @Override
    public void onPlayerResume(AudioPlayer player) {
        super.onPlayerResume(player);
        print("Audio player resumed.");
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        super.onTrackStart(player, track);
        print("Audio player started.");
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        super.onTrackEnd(player, track, endReason);
        print("Audio player terminated: "+endReason.name());
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        super.onTrackException(player, track, exception);
        exception.printStackTrace();
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        super.onTrackStuck(player, track, thresholdMs);
        print("Audio player stucked for "+thresholdMs+"ms.");
    }

    private void print(Object o){
        System.out.println(o);
    }
}
