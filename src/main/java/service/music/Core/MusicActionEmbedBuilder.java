package service.music.Core;

import Utilities.TextStyler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import service.discord.MessageEmbedProps;
import service.music.object.MusicBoxComponents;
import service.music.object.MusicPlayMode;
import service.music.object.MusicTrack;
import service.music.object.YoutubeTrackInfo;
import service.music.tools.MusicUtil;

import java.awt.*;
import java.util.ArrayList;

public class MusicActionEmbedBuilder {
    private final EmbedBuilder embedBuilder;
    private ArrayList<Button> controlButtons;
    private final StringSelectMenu.Builder trackMenuBuilder;
    private int trackCount = 0;

    public MusicActionEmbedBuilder() {
        embedBuilder = new EmbedBuilder();
        controlButtons = new ArrayList<>();
        trackMenuBuilder = StringSelectMenu.create("musicAction:musicTrackSelector");
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

    public MusicActionEmbedBuilder setTrackList(MusicTrack currentTrack, ArrayList<MusicTrack> tracks,
                                                MusicPlayMode playMode, int volume) {
        this.trackCount = tracks.size();
        if (currentTrack != null) this.trackCount++;
        for (int i = 0; i < tracks.size() && i < 25; i++) {
            YoutubeTrackInfo trackInfo = tracks.get(i).trackInfo;

            String duration = trackInfo.getDurationString();
            String requester = trackInfo.getRequester().getEffectiveName();

            String label = String.format("%d. %s", i, trackInfo.getTitle());
            String optionId = String.format("track-%s", trackInfo.getId());
            String description = String.format("[%s] %s (%s)", requester, trackInfo.getChannelTitle(), duration);

            trackMenuBuilder.addOption(label, optionId, description);
        }

        trackMenuBuilder.setPlaceholder("다음 노래가 없습니다.");

        // set image
        if (currentTrack != null) {
            YoutubeTrackInfo firstTrack = currentTrack.trackInfo;
            String duration = firstTrack.getDurationString();

            try {
                embedBuilder.setThumbnail(firstTrack.getThumbnailURL());
            } catch (IllegalArgumentException exception) {
            }

            embedBuilder.addField("현재 재생 중", TextStyler.Link(firstTrack.getTitle(), firstTrack.getVideoUrl()), false);
            embedBuilder.addField("노래 길이", TextStyler.Block(duration), true);
            embedBuilder.addField("남은 곡 수", TextStyler.Block(tracks.size() + ""), true);
            embedBuilder.addField("반복 모드", TextStyler.Block(MusicUtil.getMusicPlayModeDescription(playMode)), true);
            embedBuilder.addField("신청자", String.format("<@%s>", firstTrack.getRequester().getId()), true);
            embedBuilder.addField("채널", TextStyler.Block(firstTrack.getChannelTitle()), true);
            embedBuilder.addField("볼륨", TextStyler.Block(volume + "%"), true);

            if (tracks.size() > 0) {
                YoutubeTrackInfo nextTrack = tracks.get(0).trackInfo;
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

    public MessageEmbedProps build() {
        MessageEmbedProps pair = new MessageEmbedProps();
        pair.setMessageEmbed(embedBuilder.build());
        if (trackCount > 0) {
            pair.addActionRow(ActionRow.of(controlButtons));
            if (trackMenuBuilder.getOptions().size() > 0) {
                ActionRow trackMenuActionRow = ActionRow.of(trackMenuBuilder.build());
                pair.addActionRow(trackMenuActionRow);
            }
        }
        return pair;
    }

    public MessageEmbed buildWithoutEmbed() {
        return embedBuilder.build();
    }
}
