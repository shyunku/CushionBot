package service.music.Core;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import core.CushionBot;
import core.Version;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.Link;
import exceptions.MusicNotFoundException;
import exceptions.MusicUrlInvalidException;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.managers.AudioManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.discord.ControlBox;
import service.discord.MessageEmbedProps;
import service.guild.core.GuildUtil;
import service.inmemory.RedisClient;
import service.music.object.MusicTrack;

import java.awt.*;
import java.util.ArrayList;

public class MusicBox implements ControlBox {
    private final Logger logger = LoggerFactory.getLogger(MusicBox.class);
    private AudioManager audioManager;
    private Guild guild;
    private Message musicBoxMessage;
    //    private MusicStreamer streamer;
    private MusicStreamer streamer;

    public MusicBox(Guild guild, TextChannel musicChannel) {
        this.guild = guild;
        audioManager = guild.getAudioManager();

        String chanKey = GuildUtil.musicChannelKey(guild.getId());
        if (RedisClient.has(chanKey)) {
            String chanId = RedisClient.get(chanKey);
            TextChannel channel = guild.getTextChannelById(chanId);
            if (channel != null) {
                musicChannel = channel;
            }
        }

        if (musicChannel != null) {
            String msgKey = GuildUtil.musicBoxMessageKey(guild.getId());
            if (RedisClient.has(msgKey)) {
                String msgId = RedisClient.get(msgKey);
                try {
                    Message message = musicChannel.retrieveMessageById(msgId).complete();
                    if (message != null) {
                        musicBoxMessage = message;
                    }
                } catch (ErrorResponseException e) {
                    int errorCode = e.getErrorCode();
                    switch (errorCode) {
                        case 10008:
                            RedisClient.del(msgKey);
                            break;
                        default:
                            e.printStackTrace();
                            break;
                    }
                }
            }

            this.setMusicChannel(musicChannel);
        }
    }

    public void quickPlay(String searchQuery, Member requester) throws MusicNotFoundException, GoogleJsonResponseException, MusicUrlInvalidException {
        if (searchQuery.contains("http")) {
            String searchUrl = searchQuery;
            if (searchUrl.contains("www.youtube.com") || searchUrl.contains("music.youtube.com")) {
                if (searchUrl.contains("list=")) {
                    // playlist
                    streamer.addPlaylistByUrl(requester, searchUrl);
                } else {
                    // music link
                    streamer.addTrackByUrl(requester, searchUrl);
                }
            } else {
                throw new MusicUrlInvalidException();
            }

        } else {
            streamer.addTrackByQuery(requester, searchQuery);
        }
    }

    public MessageEmbed getInitialSettingEmbed() {
        TextChannel musicChannel = this.getMusicChannel();
        if (musicChannel == null) return null;

        MusicActionEmbedBuilder builder = new MusicActionEmbedBuilder();
        builder.setTitle(String.format(
                        "%s\"%s\" 채널이 \"%s\" 서버의 음악 채널로 지정되었습니다.",
                        Version.PRODUCTION_MODE ? "" : "[점검 모드] ",
                        musicChannel.getName(), musicChannel.getGuild().getName()))
                .setDescription("재생할 음악이 없습니다. 이 채널에 검색어를 입력하면 자동으로 재생됩니다.")
                .setColor(new Color(0, 255, 187));

        return builder.buildWithoutEmbed();
    }

    private MessageEmbedProps getCurrentMusicActionEmbed() {
        TrackScheduler scheduler = streamer.getScheduler();
        MusicTrack currentTrack = scheduler.getCurrentTrack();
        ArrayList<MusicTrack> trackInfoList = scheduler.getCurrentMusicTracks();

        AudioChannel connectedChannel = audioManager.getConnectedChannel();
        String title = "쿠션 봇 음악 재생기";
        if (connectedChannel != null) {
            title = String.format("%s에서 스트리밍 중", connectedChannel.getName());
        }
        title = (Version.PRODUCTION_MODE ? "" : "[점검모드] ") + title;

        MusicActionEmbedBuilder builder = new MusicActionEmbedBuilder();
        builder.setTitle(title)
                .setColor(new Color(0, 255, 187))
                .setControlButtons(streamer.isPaused(), scheduler.getMusicPlayMode())
                .setTrackList(currentTrack, trackInfoList, scheduler.getMusicPlayMode(), streamer.getVolume());

        if (trackInfoList.isEmpty() && currentTrack == null) {
            builder.setDescription("재생할 음악이 없습니다. 이 채널에 검색어를 입력하면 자동으로 재생됩니다.");
        }
        return builder.build();
    }

    @Override
    public void updateEmbed() {
        this.logger.debug("Updating music box embed.");
        MessageEmbedProps embed = getCurrentMusicActionEmbed();
        if (musicBoxMessage == null) {
            embed.sendMessageEmbedWithHook(getMusicChannel(), message -> {
                setMusicBoxMessage(message);
                this.updateNonNullMusicActionEmbed(embed);
            });
            return;
        }
        this.updateNonNullMusicActionEmbed(embed);
    }

    @Override
    public void clearEmbed() {
        if (musicBoxMessage == null) return;
        musicBoxMessage.delete().queue();
        musicBoxMessage = null;
        RedisClient.del(GuildUtil.musicBoxMessageKey(guild.getId()));
    }

    private void updateNonNullMusicActionEmbed(MessageEmbedProps embed) {
        assert musicBoxMessage != null;
        embed.editMessageEmbed(musicBoxMessage);
    }

    public MusicStreamer getStreamer() {
        return streamer;
    }

    public TextChannel getMusicChannel() {
        if (streamer == null) return null;
        return streamer.getMusicChannel();
    }

    public void setMusicChannel(TextChannel musicChannel) {
        if (musicChannel == null) return;
        LavalinkClient client = CushionBot.lavalinkClient;
        if (client == null) {
            logger.error("Lavalink client is not initialized.");
            return;
        }

        if (this.streamer != null) {
            this.streamer.destroy();
        }

        Link link = client.getOrCreateLink(guild.getIdLong());
        this.streamer = new MusicStreamer(musicChannel, link, this::updateEmbed);
        this.streamer.clearTracksOfQueue();

        // initial update embed
        this.updateEmbed();
        RedisClient.set(GuildUtil.musicChannelKey(guild.getId()), musicChannel.getId());
    }

    public void setMusicBoxMessage(Message musicBoxMessage) {
        if (musicBoxMessage == null) return;
        this.musicBoxMessage = musicBoxMessage;
        RedisClient.set(GuildUtil.musicBoxMessageKey(guild.getId()), musicBoxMessage.getId());
    }
}
