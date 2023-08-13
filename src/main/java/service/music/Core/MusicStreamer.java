package service.music.Core;


import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import service.music.object.MusicPlayMode;
import service.music.object.YoutubeTrackInfo;
import service.music.tools.MusicUtil;

public class MusicStreamer {
    private final AudioPlayer audioPlayer;
    private final TrackScheduler trackScheduler;
    private TextChannel musicChannel;
    private final AudioPlayerManager audioPlayerManager;

    public MusicStreamer(TextChannel textChannel, AudioManager audioManager, MusicBoxUpdateHandler musicBoxUpdateHandler) {
        this.audioPlayerManager = new DefaultAudioPlayerManager();

        this.audioPlayerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
        AudioSourceManagers.registerLocalSource(audioPlayerManager);
        AudioSourceManagers.registerRemoteSources(audioPlayerManager);

        audioPlayer = audioPlayerManager.createPlayer();

        AudioPlayerSendHandler sendHandler = new AudioPlayerSendHandler(audioPlayer);
        audioManager.setSendingHandler(sendHandler);

        this.musicChannel = textChannel;

        trackScheduler = new TrackScheduler(audioPlayer, musicBoxUpdateHandler);
        audioPlayer.addListener(trackScheduler);
    }

    public void addTrackToQueue(Member requester, YoutubeTrackInfo trackInfo) {
        trackScheduler.addTrackData(trackInfo);
        this.loadItem(requester, trackInfo);
    }

    // if requester requested tracks as youtube music playlist
    public void addTrackListToQueue(Member requester, String url) {
//        trackScheduler.addTrackData(trackInfo);
        this.loadItemList(requester, url);
    }

    public void repeatTrackToQueue(MusicPlayMode musicPlayMode) {
        trackScheduler.setMusicPlayMode(musicPlayMode);
    }

    public void shuffleTracksOnQueue() {
        trackScheduler.shuffleTracks();
    }

    public void clearTracksOfQueue() {
        trackScheduler.clearTracks();
    }

    public void skipCurrentTracksOfQueue() {
        trackScheduler.nextTrack();
    }

    public String getPlayModeDescription() {
        return MusicUtil.getMusicPlayModeDescription(trackScheduler.getMusicPlayMode());
    }

    public TrackScheduler getTrackScheduler() {
        return trackScheduler;
    }

    /* ----------------------------- Internal Functions ----------------------------- */
    private void loadItem(Member requester, YoutubeTrackInfo trackInfo) {
        CustomAudioResultHandler handler = new CustomAudioResultHandler(requester, musicChannel, trackScheduler);
        this.audioPlayerManager.loadItemOrdered(this, trackInfo.getVideoUrl(), handler);
    }

    private void loadItemList(Member requester, String url) {
        CustomAudioResultHandler handler = new CustomAudioResultHandler(requester, musicChannel, trackScheduler);
        this.audioPlayerManager.loadItemOrdered(this, url, handler);
    }

    public boolean isPaused() {
        return this.audioPlayer.isPaused();
    }

    public void setPaused(boolean pause) {
        this.audioPlayer.setPaused(pause);
    }

    public TextChannel getMusicChannel() {
        return musicChannel;
    }

    public void setMusicChannel(TextChannel channel) {
        this.musicChannel = channel;
    }

    public AudioPlayer getAudioPlayer() {
        return audioPlayer;
    }
}
