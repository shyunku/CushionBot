package service.music.Core;

import Utilities.TextStyler;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import core.command.CommandRouter;
import exceptions.MusicNotFoundException;
import net.dv8tion.jda.api.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.inmemory.RedisClient;
import service.music.object.YoutubeTrackInfo;
import service.music.tools.MusicUtil;
import service.music.tools.YoutubeCrawler;
import net.dv8tion.jda.api.managers.AudioManager;

import java.awt.*;
import java.util.ArrayList;

public class MusicBox {
    private final Logger logger = LoggerFactory.getLogger(MusicBox.class);
    private AudioManager audioManager;
    private Guild guild;
    private Message musicBoxMessage;
    private MusicStreamer streamer;

    public MusicBox(Guild guild, TextChannel musicChannel) {
        this.guild = guild;
        audioManager = guild.getAudioManager();

        String chanKey = MusicUtil.musicChannelKey(guild.getId());
        if(RedisClient.has(chanKey)) {
            String chanId = RedisClient.get(chanKey);
            TextChannel channel = guild.getTextChannelById(chanId);
            if(channel != null) {
                musicChannel = channel;
            }
        }

        String msgKey = MusicUtil.musicBoxMessageKey(guild.getId());
        if(RedisClient.has(msgKey)) {
            String msgId = RedisClient.get(msgKey);
            Message message = musicChannel.retrieveMessageById(msgId).complete();
            if(message != null) {
                musicBoxMessage = message;
            }
        }

        streamer = new MusicStreamer(musicChannel, audioManager, new MusicTrackEndHandler() {
            @Override
            public void onTrackEnd() {
                updateMusicActionEmbed();
            }
        });
    }

    public void quickPlay(String searchQuery, Member requester) throws MusicNotFoundException, GoogleJsonResponseException {
        if(searchQuery.contains("www.youtube.com") || searchQuery.contains("music.youtube.com")) {
            streamer.addTrackListToQueue(requester, searchQuery);
        } else {
            ArrayList<YoutubeTrackInfo> trackCandidates = YoutubeCrawler.getVideoCandidates(searchQuery, 1, requester);
            if(trackCandidates.isEmpty()){
                throw new MusicNotFoundException();
            }
            YoutubeTrackInfo selectedTrackInfo = trackCandidates.get(0);
            streamer.addTrackToQueue(requester, selectedTrackInfo);
        }
    }

    public MessageEmbed getInitialSettingEmbed() {
        TextChannel musicChannel = this.getMusicChannel();
        if(musicChannel == null) return null;

        MusicActionEmbedBuilder builder = new MusicActionEmbedBuilder();
        builder.setTitle(String.format(
                "\"%s\" 채널이 \"%s\" 서버의 음악 채널로 지정되었습니다.",
                        musicChannel.getName(), musicChannel.getGuild().getName()))
                .setDescription("이 채널에 검색어를 입력하면 자동으로 재생됩니다.")
                .setColor(new Color(0, 255, 187));

        return builder.buildWithoutEmbed();
    }

    public MusicActionEmbed getCurrentMusicActionEmbed(){
        ArrayList<YoutubeTrackInfo> trackInfoList = streamer.getTrackScheduler().getTrackDataList();

        AudioChannel connectedChannel = audioManager.getConnectedChannel();
        String title = "쿠션 봇 음악 재생기";
        if(connectedChannel != null) {
            title = String.format("%s에서 스트리밍 중", connectedChannel.getName());
        }

        AudioPlayer audioPlayer = this.streamer.getAudioPlayer();

        MusicActionEmbedBuilder builder = new MusicActionEmbedBuilder();
        builder.setTitle(title)
                .setColor(new Color(0, 255, 187))
                .setControlButtons(streamer.isPaused(), streamer.getTrackScheduler().getMusicPlayMode())
                .setTrackList(trackInfoList, audioPlayer.getPlayingTrack(), streamer.getTrackScheduler().getMusicPlayMode());

        return builder.build();
    }

    public void updateMusicActionEmbed(){
        MusicActionEmbed embed = getCurrentMusicActionEmbed();
        if(musicBoxMessage == null) {
            getMusicChannel().sendMessageEmbeds(embed.messageEmbed).queue(message -> {
                musicBoxMessage = message;
                this.updateNonNullMusicActionEmbed(embed);
            });
            return;
        }
        this.updateNonNullMusicActionEmbed(embed);
    }

    private void updateNonNullMusicActionEmbed(MusicActionEmbed embed) {
        if(embed.musicTrackSelector == null) {
            musicBoxMessage.editMessageEmbeds(embed.messageEmbed)
                    .setActionRows(embed.musicController).queue();
            return;
        }

        musicBoxMessage.editMessageEmbeds(embed.messageEmbed)
                .setActionRows(embed.musicTrackSelector, embed.musicController).queue();
    }

    public MusicStreamer getStreamer() {
        return streamer;
    }

    public TextChannel getMusicChannel() {
        return streamer.getMusicChannel();
    }

    public void setMusicChannel(TextChannel musicChannel) {
        if(musicChannel == null) return;
        this.streamer.setMusicChannel(musicChannel);
        RedisClient.set(MusicUtil.musicChannelKey(guild.getId()), musicChannel.getId());
    }

    public void setMusicBoxMessage(Message musicBoxMessage) {
        if(musicBoxMessage == null) return;
        this.musicBoxMessage = musicBoxMessage;
        RedisClient.set(MusicUtil.musicBoxMessageKey(guild.getId()), musicBoxMessage.getId());
    }
}
