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
import java.util.Arrays;
import java.util.stream.Collectors;

public class MusicBox implements ControlBox {
    private final Logger logger = LoggerFactory.getLogger(MusicBox.class);
    private AudioManager audioManager;
    private Guild guild;
    private volatile Message musicBoxMessage;
    private boolean postingBox = false;
    private boolean updateAfterPost = false;
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
                    musicBoxMessage = musicChannel.retrieveMessageById(msgId).complete();
                } catch (ErrorResponseException e) {
                    if (e.getErrorCode() == 10008) RedisClient.del(msgKey);
                    else logger.warn("Could not load the music box in {}: {}", guild.getName(), e.getMessage());
                } catch (RuntimeException e) {
                    // e.g. missing "read message history"; a new box is posted on the next update
                    logger.warn("Could not load the music box in {}: {}", guild.getName(), e.getMessage());
                }
            }

            this.setMusicChannel(musicChannel);
        }
    }

    public void quickPlay(String searchQuery, Member requester) throws MusicNotFoundException, GoogleJsonResponseException, MusicUrlInvalidException {
        if (searchQuery.contains("http")) {
            String searchUrl = searchQuery.trim();
            if (isYoutubeUrl(searchUrl)) {
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

    /** youtube.com (www, m, music) and youtu.be links. */
    static boolean isYoutubeUrl(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase();
            return host.equals("youtu.be") || host.equals("youtube.com") || host.endsWith(".youtube.com");
        } catch (IllegalArgumentException e) {
            return false;
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
        if (streamer == null) return;
        MessageEmbedProps embed = getCurrentMusicActionEmbed();
        Message message;
        synchronized (this) {
            message = musicBoxMessage;
            if (message == null) {
                // one box at a time: updates arriving while it is being posted are folded into a follow-up
                if (postingBox) {
                    updateAfterPost = true;
                    return;
                }
                postingBox = true;
            }
        }
        if (message == null) {
            embed.sendMessageEmbedWithHook(getMusicChannel(), posted -> {
                setMusicBoxMessage(posted);
                boolean again;
                synchronized (this) {
                    postingBox = false;
                    again = updateAfterPost;
                    updateAfterPost = false;
                }
                if (again) updateEmbed();
            });
            return;
        }
        // the box was deleted (e.g. /clear): forget it and post a new one
        embed.editMessageEmbed(message, () -> {
            synchronized (this) {
                if (musicBoxMessage != message) return;
                musicBoxMessage = null;
            }
            updateEmbed();
        });
    }

    /** Deletes the box and waits for Discord, so it is safe right before the process exits. */
    @Override
    public void clearEmbed() {
        Message message;
        synchronized (this) {
            message = musicBoxMessage;
            musicBoxMessage = null;
        }
        RedisClient.del(GuildUtil.musicBoxMessageKey(guild.getId()));
        if (message == null) return;
        try {
            message.delete().complete();
        } catch (RuntimeException e) {
            logger.warn("Could not delete the music box in {}: {}", guild.getName(), e.getMessage());
        }
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
            // same channel again: keep the player and queue as they are
            if (this.streamer.getMusicChannel().getId().equals(musicChannel.getId())) {
                RedisClient.set(GuildUtil.musicChannelKey(guild.getId()), musicChannel.getId());
                return;
            }
            this.streamer.destroy();
            // the old box lives in the old channel; remove it so its buttons do not linger
            Message old;
            synchronized (this) {
                old = musicBoxMessage;
                musicBoxMessage = null;
            }
            if (old != null) old.delete().queue(null, failure -> {
            });
        }

        Link link = client.getOrCreateLink(guild.getIdLong());
        try {
            this.streamer = new MusicStreamer(musicChannel, link, this::updateEmbed);
            this.streamer.clearTracksOfQueue();
        } catch (RuntimeException e) {
            logger.error("Could not create the music player for {}", guild.getName(), e);
            this.streamer = null;
            return;
        }

        // initial update embed
        // logger.info("updating embed after setting music channel");
        // this.updateEmbed();
        RedisClient.set(GuildUtil.musicChannelKey(guild.getId()), musicChannel.getId());
    }

    public void setMusicBoxMessage(Message musicBoxMessage) {
        if (musicBoxMessage == null) return;
        logger.info("Setting music box message with ID: {}", musicBoxMessage.getId());
        Message old;
        synchronized (this) {
            old = this.musicBoxMessage;
            this.musicBoxMessage = musicBoxMessage;
        }
        if (old != null && !old.getId().equals(musicBoxMessage.getId())) old.delete().queue(null, failure -> {
        });
        RedisClient.set(GuildUtil.musicBoxMessageKey(guild.getId()), musicBoxMessage.getId());
    }
}
