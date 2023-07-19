package service.music.Core;

import Utilities.TextStyler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import service.music.object.MusicBoxComponents;
import service.music.object.MusicPlayMode;
import service.music.object.YoutubeTrackInfo;
import service.music.tools.MusicUtil;

import java.awt.Color;
import java.util.ArrayList;

public class MusicActionEmbedBuilder {
    private final EmbedBuilder embedBuilder;
    private ArrayList<Button> controlButtons;
    private final SelectMenu.Builder trackMenuBuilder;

    public MusicActionEmbedBuilder() {
        embedBuilder = new EmbedBuilder();
        controlButtons = new ArrayList<>();
        trackMenuBuilder = SelectMenu.create("musicAction:musicTrackSelector");
    }

    public MusicActionEmbedBuilder setTitle(String title) {
        embedBuilder.setTitle(title);
        return this;
    }

    public MusicActionEmbedBuilder setDescription(String description) {
        embedBuilder.setDescription(description);
        return this;
    }

    public MusicActionEmbedBuilder setColor(Color color) {
        embedBuilder.setColor(color);
        return this;
    }

    public MusicActionEmbedBuilder setTrackList(ArrayList<YoutubeTrackInfo> tracks, AudioTrack currentTrack, MusicPlayMode playMode) {
        for (int i=1; i<tracks.size() && i<=25; i++) {
            YoutubeTrackInfo trackInfo = tracks.get(i);

            String duration = trackInfo.getDurationString();

            String label = String.format("%d. %s", i, trackInfo.getTitle());
            String optionId = String.format("track-%s", trackInfo.getId());
            String description = String.format("[%s] %s - %s", duration, trackInfo.getChannelTitle(), trackInfo.getRequester().getNickname());

            trackMenuBuilder.addOption(label, optionId, description);
        }

        trackMenuBuilder.setPlaceholder("다음 노래가 없습니다.");

        // set image
        if(!tracks.isEmpty()){
            YoutubeTrackInfo firstTrack = tracks.get(0);
            String duration = firstTrack.getDurationString();

            try {
                embedBuilder.setThumbnail(firstTrack.getThumbnailURL());
            } catch (IllegalArgumentException excpetion) {}

            embedBuilder.addField("현재 재생 중", TextStyler.Link(firstTrack.getTitle(), firstTrack.getVideoUrl()), false);
            embedBuilder.addField("노래 길이", TextStyler.Block(duration), true);
            embedBuilder.addField("남은 곡 수", TextStyler.Block(tracks.size() - 1 + ""), true);
            embedBuilder.addField("반복 모드", TextStyler.Block(MusicUtil.getMusicPlayModeDescription(playMode)), true);
            embedBuilder.addField("신청자", String.format("<@%s>", firstTrack.getRequester().getId()), true);
            embedBuilder.addField("채널", TextStyler.Block(firstTrack.getChannelTitle()), true);
            embedBuilder.addField("개발자", TextStyler.Block("shyunku"), true);

            if(tracks.size() > 1) {
                YoutubeTrackInfo nextTrack = tracks.get(1);
                trackMenuBuilder.setPlaceholder(String.format("다음: %s", nextTrack.getTitle()));
            }
        }

        return this;
    }

    public MusicActionEmbedBuilder setControlButtons(boolean paused, MusicPlayMode playMode) {
        controlButtons.add(MusicBoxComponents.MusicStopButton);
        controlButtons.add(paused ? MusicBoxComponents.MusicPlayButton : MusicBoxComponents.MusicPauseButton);
        controlButtons.add(MusicBoxComponents.MusicSkipButton);
        controlButtons.add(MusicUtil.getMusicRepeatModeButton(playMode));
        controlButtons.add(MusicBoxComponents.MusicLeaveButton);
        return this;
    }

    public MusicActionEmbed build() {
        ActionRow trackMenuActionRow = ActionRow.of(trackMenuBuilder.build());
        if(trackMenuBuilder.getOptions().size() == 0) {
            trackMenuActionRow = null;
        }
        return new MusicActionEmbed(embedBuilder.build(), ActionRow.of(controlButtons), trackMenuActionRow);
    }

    public MessageEmbed buildWithoutEmbed() {
        return embedBuilder.build();
    }
}
