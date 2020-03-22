package music.Core;


import Utilities.TextStyleManager;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import music.object.MusicPlayMode;
import music.object.YoutubeTrackInfo;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.managers.AudioManager;

import java.util.ArrayList;
import java.util.List;

public class MusicStreamer {
    private AudioPlayer audioPlayer;
    private TrackScheduler trackScheduler;
    private TextChannel textChannel;

    public MusicStreamer(AudioManager audioManager, AudioPlayerManager audioPlayerManager, TextChannel textChannel){
        audioPlayer = audioPlayerManager.createPlayer();
        this.textChannel = textChannel;
        trackScheduler = new TrackScheduler(audioPlayer, audioManager, textChannel);
        audioPlayer.addListener(trackScheduler);
    }

    public AudioPlayer getAudioPlayer(){
        return audioPlayer;
    }

    public TrackScheduler getTrackScheduler(){
        return trackScheduler;
    }

    public void loadItem(AudioPlayerManager audioPlayerManager, YoutubeTrackInfo trackInfo){
        audioPlayerManager.loadItemOrdered(
                this,
                "https://www.youtube.com/watch?v=" + trackInfo.getId(),
                audioLoadResultHandler
                );
    }

    public void loadItemList(AudioPlayerManager audioPlayerManager, String url){
        audioPlayerManager.loadItemOrdered(
                this,
                url,
                audioLoadResultHandler
        );
    }

    private final AudioLoadResultHandler audioLoadResultHandler = new AudioLoadResultHandler() {
        @Override
        public void trackLoaded(AudioTrack track) {
            trackScheduler.addTrackToQueue(track);
            StringBuilder message = new StringBuilder();
            message.append("플레이리스트에 추가되었습니다: ");

            long duration = track.getDuration()/1000;
            long hour = duration/3600;
            long min = (duration%3600)/60;
            long sec = duration%60;
            String timeStr = " (" + (hour>0?hour+":":"");
            timeStr += String.format("%02d",min)+":";
            timeStr += String.format("%02d",sec)+")";

            message.append(textStyler.toBold(track.getInfo().title));
            message.append(timeStr);

            textChannel.sendMessage(message.toString()).queue();
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            List<AudioTrack> trackList = playlist.getTracks();
            for(AudioTrack track : trackList){
                trackScheduler.addTrackToQueue(track);
            }
            textChannel.sendMessage("플레이리스트"+
                    textStyler.toBold(playlist.getName())+": "+trackList.size()+"개의 트랙이 로드되었습니다.").queue();
        }

        @Override
        public void noMatches() {
            textChannel.sendMessage("매치되는 결과가 없습니다.").queue();
        }

        @Override
        public void loadFailed(FriendlyException exception) {
            textChannel.sendMessage("load failed.").queue();
        }
    };

    private final TextStyleManager textStyler = new TextStyleManager();
}
