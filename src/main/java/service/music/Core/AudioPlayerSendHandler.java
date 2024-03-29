package service.music.Core;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AudioPlayerSendHandler implements AudioSendHandler {
    private final AudioPlayer audioPlayer;
    private AudioFrame lastFrame;

    public AudioPlayerSendHandler(AudioPlayer audioPlayer) {
        this.audioPlayer = audioPlayer;
    }

    @Override
    public boolean canProvide() {
        setFrame();
        return lastFrame != null;
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        setFrame();
        byte[] data = lastFrame != null ? lastFrame.getData() : null;
        lastFrame = null;

        return ByteBuffer.wrap(data);
    }

    @Override
    public boolean isOpus() {
        return true;
    }

    private void setFrame(){
        if(lastFrame == null){
            lastFrame = audioPlayer.provide();
        }
    }
}