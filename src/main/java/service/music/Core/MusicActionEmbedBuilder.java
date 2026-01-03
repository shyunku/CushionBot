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
                                                MusicPlayMode playMode, long volume) {
        this.trackCount = tracks.size();
        if (currentTrack != null) this.trackCount++;
        for (int i = 0; i < tracks.size() && i < 25; i++) {
            MusicTrack musicTrack = tracks.get(i);

            String duration = musicTrack.getDurationString();
            String requester = musicTrack.requester.getEffectiveName();

            String label = String.format("%d. %s", i, musicTrack.getTitle());
            String optionId = String.format("track-%d-%s", i, musicTrack.getIdentifier());
            String description = String.format("[%s] %s (%s)", requester, musicTrack.getChannelTitle(), duration);

            trackMenuBuilder.addOption(label, optionId, description);
        }

        trackMenuBuilder.setPlaceholder("다음 노래가 없습니다.");

        // set image
        if (currentTrack != null) {
            String duration = currentTrack.getDurationString();

            try {
                embedBuilder.setThumbnail(currentTrack.getThumbnailURL());
            } catch (IllegalArgumentException exception) {
            }

            embedBuilder.addField("현재 재생 중", TextStyler.Link(currentTrack.getTitle(), currentTrack.getVideoURL()), false);
            embedBuilder.addField("노래 길이", TextStyler.Block(duration), true);
            embedBuilder.addField("남은 곡 수", TextStyler.Block(tracks.size() + ""), true);
            embedBuilder.addField("반복 모드", TextStyler.Block(MusicUtil.getMusicPlayModeDescription(playMode)), true);
            embedBuilder.addField("신청자", String.format("<@%s>", currentTrack.requester.getId()), true);
            embedBuilder.addField("채널", TextStyler.Block(currentTrack.getChannelTitle()), true);
            embedBuilder.addField("볼륨", TextStyler.Block(volume + "%"), true);
//            embedBuilder.addField("업데이트 시간", String.valueOf(System.currentTimeMillis()), false);

            if (tracks.size() > 0) {
                MusicTrack nextTrack = tracks.get(0);
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
