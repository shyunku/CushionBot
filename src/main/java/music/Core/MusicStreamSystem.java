package music.Core;

import Utilities.TextStyleManager;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import music.object.MusicPlayMode;
import music.object.YoutubeTrackInfo;
import music.tools.AudioPlayerSendHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.managers.AudioManager;

import java.util.HashMap;
import java.util.Map;

public class MusicStreamSystem {
    private Map<Long, MusicStreamer> streamerMap = new HashMap<>();

    public void registerMusicStreamer(AudioManager audioManager, AudioPlayerManager audioPlayerManager, TextChannel textChannel){
        Guild guild = textChannel.getGuild();
        long guildId = guild.getIdLong();
        MusicStreamer musicStreamer = new MusicStreamer(audioManager, audioPlayerManager, textChannel);
        streamerMap.put(guildId, musicStreamer);
    }

    public void addTrackToQueue(TextChannel textChannel, AudioPlayerManager audioPlayerManager, YoutubeTrackInfo trackInfo){
        MusicStreamer musicStreamer = getMusicStreamer(textChannel);
        TrackScheduler trackScheduler = musicStreamer.getTrackScheduler();
        trackScheduler.addTrackData(trackInfo);
        musicStreamer.loadItem(audioPlayerManager, trackInfo);
    }

    public void repeatTrackToQueue(TextChannel textChannel, MusicPlayMode musicPlayMode){
        MusicStreamer musicStreamer = getMusicStreamer(textChannel);
        musicStreamer.getTrackScheduler().setMusicPlayMode(musicPlayMode);
    }

    public MusicStreamer getMusicStreamer(TextChannel textChannel){
        Guild guild = textChannel.getGuild();
        long guildId = guild.getIdLong();
//        if(streamerMap.get(guildId) == null){
//            //스트리머가 없을 경우 새로 생성
//            registerMusicStreamer(audioManager, textChannel);
//        }
        MusicStreamer musicStreamer = streamerMap.get(guildId);
        guild.getAudioManager().setSendingHandler(new AudioPlayerSendHandler(musicStreamer.getAudioPlayer()));

        return musicStreamer;
    }



    private final TextStyleManager textStyler = new TextStyleManager();
}
