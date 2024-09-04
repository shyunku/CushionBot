package service.music.Core;

import Utilities.TextStyler;
import dev.arbjerg.lavalink.client.AbstractAudioLoadResultHandler;
import dev.arbjerg.lavalink.client.player.*;
import dev.arbjerg.lavalink.protocol.v4.TrackInfo;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.music.object.MusicTrack;
import service.music.tools.MusicUtil;

import java.time.Duration;
import java.util.List;

public class LavaAudioLoader extends AbstractAudioLoadResultHandler {
    private final Logger logger = LoggerFactory.getLogger(LavaAudioLoader.class);
    private Member requester;
    private final TextChannel musicChannel;
    private final TrackScheduler scheduler;

    public LavaAudioLoader(Member requester, TextChannel musicChannel, TrackScheduler scheduler) {
        this.requester = requester;
        this.musicChannel = musicChannel;
        this.scheduler = scheduler;
    }

    @Override
    public void ontrackLoaded(@NotNull TrackLoaded trackLoaded) {
        Track track = trackLoaded.getTrack();
        TrackInfo trackInfo = track.getInfo();
        scheduler.addMusicTrack(new MusicTrack(requester, track));
        logger.info("Track loaded: {}", trackInfo.getTitle());

        Duration duration = Duration.ofMillis(trackInfo.getLength());
        StringBuilder message = new StringBuilder();
        message.append("플레이리스트에 추가되었습니다: ");
        message.append(TextStyler.Bold(trackInfo.getTitle()));
        message.append(String.format(" (%s)", MusicUtil.getDurationString(duration)));

        musicChannel.sendMessage(message.toString()).queue(sentMessage -> {
            sentMessage.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS);
        });

        scheduler.getMusicBoxUpdateHandler().onActionEnd();
    }

    @Override
    public void onPlaylistLoaded(@NotNull PlaylistLoaded playlistLoaded) {
        List<Track> trackList = playlistLoaded.getTracks();
        for (Track track : trackList) {
            scheduler.addMusicTrack(new MusicTrack(requester, track));
        }
        logger.info("Playlist loaded: {}", playlistLoaded.getInfo().getName());

        musicChannel.sendMessage("플레이리스트 " +
                TextStyler.Bold(playlistLoaded.getInfo().getName()) + " : " + trackList.size() + "개의 트랙이 로드되었습니다.").queue(sentMessage -> {
            sentMessage.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS);
        });

        scheduler.getMusicBoxUpdateHandler().onActionEnd();
    }

    @Override
    public void onSearchResultLoaded(@NotNull SearchResult searchResult) {
        logger.info("Search result loaded: {}", searchResult.getTracks().size());
        Track firstTrack = null;
        int index = 0;
        for (Track track : searchResult.getTracks()) {
            if (index == 0) {
                firstTrack = track;
            }
            logger.info("\t- {}.{}", ++index, track.getInfo().getTitle());
            if (index >= 5) {
                break;
            }
        }

        if (firstTrack != null) {
            TrackInfo trackInfo = firstTrack.getInfo();
            scheduler.addMusicTrack(new MusicTrack(requester, firstTrack));

            Duration duration = Duration.ofMillis(trackInfo.getLength());
            StringBuilder message = new StringBuilder();
            message.append("플레이리스트에 추가되었습니다: ");
            message.append(TextStyler.Bold(trackInfo.getTitle()));
            message.append(String.format(" (%s)", MusicUtil.getDurationString(duration)));

            musicChannel.sendMessage(message.toString()).queue(sentMessage -> {
                sentMessage.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS);
            });

            scheduler.getMusicBoxUpdateHandler().onActionEnd();
        } else {
            musicChannel.sendMessage("검색 결과가 없습니다.").queue(message -> {
                message.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS);
            });
        }
    }

    @Override
    public void loadFailed(@NotNull LoadFailed loadFailed) {
        logger.error("Failed to load track: {}", loadFailed.getException());
        musicChannel.sendMessage("로드 실패: " + loadFailed.getException().getMessage()).queue(message -> {
            message.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS);
        });
    }

    @Override
    public void noMatches() {
        logger.info("No matches found");
        musicChannel.sendMessage("매치되는 결과가 없습니다.").queue(message -> {
            message.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS);
        });
    }
}
