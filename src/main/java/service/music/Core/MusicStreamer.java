package service.music.Core;


import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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

        YoutubeAudioSourceManager youtubeAudioSourceManager = new dev.lavalink.youtube.YoutubeAudioSourceManager();
        this.audioPlayerManager.registerSourceManager(youtubeAudioSourceManager);

//        AudioSourceManagers.registerLocalSource(audioPlayerManager);
//        AudioSourceManagers.registerRemoteSources(this.audioPlayerManager, com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class);

        audioPlayer = audioPlayerManager.createPlayer();

        AudioPlayerSendHandler sendHandler = new AudioPlayerSendHandler(audioPlayer);
        audioManager.setSendingHandler(sendHandler);

        this.musicChannel = textChannel;

        trackScheduler = new TrackScheduler(audioPlayer, musicBoxUpdateHandler);
        audioPlayer.addListener(trackScheduler);
    }

    public void addTrackToQueue(Member requester, YoutubeTrackInfo track) {
        this.loadItem(requester, track);
    }

    // if requester requested tracks as youtube music link
    public void addTrackToQueue(Member requester, String url) {
        this.loadItemList(requester, url);
    }

    // if requester requested tracks as youtube music playlist
    public void addTrackListToQueue(Member requester, String url) {
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
        trackScheduler.removeCurrentTrack();
    }

    public void skipCurrentTracksOfQueue() {
        trackScheduler.nextTrack();
    }

    public String getPlayModeDescription() {
        return MusicUtil.getMusicPlayModeDescription(trackScheduler.getMusicPlayMode());
    }

    public void setVolume(int volume) {
        audioPlayer.setVolume(volume);
    }

    public int getVolume() {
        return audioPlayer.getVolume();
    }

    public TrackScheduler getTrackScheduler() {
        return trackScheduler;
    }

    /* ----------------------------- Internal Functions ----------------------------- */
    private void loadItem(Member requester, YoutubeTrackInfo trackInfo) {
        CustomAudioResultHandler handler = new CustomAudioResultHandler(requester, musicChannel, trackScheduler, trackInfo);
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
