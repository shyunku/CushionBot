package music.Core;

import Utilities.TextStyleManager;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import core.command.CommandStatus;
import music.object.MusicPlayMode;
import music.object.YoutubeTrackInfo;
import music.tools.AudioPlayerSendHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.managers.AudioManager;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MusicStreamSystem {
    private CommandStatus mode = CommandStatus.NORMAL;
    private Map<Long, MusicStreamer> streamerMap = new HashMap<>();

    public void registerMusicStreamer(AudioManager audioManager, AudioPlayerManager audioPlayerManager, TextChannel textChannel){
        Guild guild = textChannel.getGuild();
        long guildId = guild.getIdLong();
        if(streamerMap.get(guildId) != null) return;
        MusicStreamer musicStreamer = new MusicStreamer(audioManager, audioPlayerManager, textChannel);
        streamerMap.put(guildId, musicStreamer);
    }

    public void addTrackToQueue(TextChannel textChannel, AudioPlayerManager audioPlayerManager, YoutubeTrackInfo trackInfo){
        MusicStreamer musicStreamer = getMusicStreamer(textChannel);
        TrackScheduler trackScheduler = musicStreamer.getTrackScheduler();
        trackScheduler.addTrackData(trackInfo);
        musicStreamer.loadItem(audioPlayerManager, trackInfo);
    }

    public void addTrackListToQueue(TextChannel textChannel, AudioPlayerManager audioPlayerManager, String url){
        MusicStreamer musicStreamer = getMusicStreamer(textChannel);
        TrackScheduler trackScheduler = musicStreamer.getTrackScheduler();
//        trackScheduler.addTrackData(trackInfo);
        musicStreamer.loadItemList(audioPlayerManager, url);
    }

    public void repeatTrackToQueue(TextChannel textChannel, MusicPlayMode musicPlayMode){
        MusicStreamer musicStreamer = getMusicStreamer(textChannel);
        musicStreamer.getTrackScheduler().setMusicPlayMode(musicPlayMode);
    }

    public void clearTracksOfQueue(TextChannel textChannel){
        MusicStreamer musicStreamer = getMusicStreamer(textChannel);
        musicStreamer.getTrackScheduler().clearTracks();
    }

    public void skipCurrentTracksOfQueue(TextChannel textChannel){
        MusicStreamer musicStreamer = getMusicStreamer(textChannel);
        musicStreamer.getTrackScheduler().nextTrack();
    }

    public String getPlayModeDescription(TextChannel textChannel){
        MusicStreamer musicStreamer = getMusicStreamer(textChannel);
        return musicStreamer.getTrackScheduler().getMusicPlayModeDescription();
    }

    public MusicStreamer getMusicStreamer(TextChannel textChannel){
        Guild guild = textChannel.getGuild();
        long guildId = guild.getIdLong();
        MusicStreamer musicStreamer = streamerMap.get(guildId);
        guild.getAudioManager().setSendingHandler(new AudioPlayerSendHandler(musicStreamer.getAudioPlayer()));

        return musicStreamer;
    }

    public void printMusicBox(TextChannel textChannel) {
        ArrayList<YoutubeTrackInfo> trackInfoList = this.getMusicStreamer(textChannel).getTrackScheduler().getTrackDataList();
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Current Track List");
        embedBuilder.setDescription("현재 "+trackInfoList.size()+"개의 트랙이 있습니다. - 재생모드: "
                +textStyler.toBold(this.getPlayModeDescription(textChannel)));
        embedBuilder.setColor(new Color(0, 255, 187));
        ArrayList<StringBuilder> stringBuilders = new ArrayList<>();
        StringBuilder trackInfoListBlock = new StringBuilder();

        int index = 1;
        for(YoutubeTrackInfo trackInfo : trackInfoList){
            int nextTotalStringLen = trackInfoListBlock.toString().length() + 7 + trackInfo.getTitle().length();
            if(nextTotalStringLen >= 1024){
                stringBuilders.add(trackInfoListBlock);
                trackInfoListBlock = new StringBuilder();
            }
            trackInfoListBlock.append(index++).append(". ").append(trackInfo.getTitle());
            if(index <= trackInfoList.size())
                trackInfoListBlock.append("\n");
        }
        stringBuilders.add(trackInfoListBlock);

        for(int i=0;i<stringBuilders.size();i++){
            @Nullable String tmpTitle = "트랙리스트 페이지 "+(i+1)+"/"+stringBuilders.size();
            embedBuilder.addField(textStyler.toBold(tmpTitle), stringBuilders.get(i).toString(), false);
        }

        MusicPlayMode nextPlayMode = this.getMusicStreamer(textChannel).getTrackScheduler().getNextMusicPlayMode();
        Button nextPlayModeButton = TrackScheduler.getMusicPlayModeButton(nextPlayMode);

        ActionRow actionRow = ActionRow.of(
                Button.danger("musicBoxStop", "■"),
                Button.secondary("musicBoxPause", "⏸"),
                Button.primary("musicBoxPlay", "▶"),
                Button.secondary("musicBoxNext", "⏭"),
                nextPlayModeButton
        );

        textChannel.sendMessageEmbeds(embedBuilder.build()).setActionRows(actionRow).queue();
    }

    private boolean isMusicStreamerNull(TextChannel textChannel){
        Guild guild = textChannel.getGuild();
        long guildId = guild.getIdLong();
        if(streamerMap.get(guildId) == null){
            textChannel.sendMessage("StreamerMap has no textchannel matched to guild!").queue();
        }
        return streamerMap.get(guildId) == null;
    }

    private final TextStyleManager textStyler = new TextStyleManager();
}
