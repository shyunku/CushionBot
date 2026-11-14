package service.music.object;

import net.dv8tion.jda.api.components.buttons.Button;

public class MusicBoxComponents {
    public static final Button MusicStopButton = Button.danger("musicBoxStop", "■");
    public static final Button MusicPauseButton = Button.primary("musicBoxPause", "⏸");
    public static final Button MusicPlayButton = Button.primary("musicBoxPlay", "▶");
    public static final Button MusicSkipButton = Button.secondary("musicBoxSkip", "⏭");
    // use icon X
    public static final Button MusicLeaveButton = Button.secondary("musicBoxLeave", "❌");

    public static final Button MusicNormalModeButton = Button.secondary("musicBoxRepeat", "🔁");
    public static final Button MusicRepeatAllModeButton = Button.success("musicBoxRepeat", "🔁");
    public static final Button MusicRepeatSingleModeButton = Button.success("musicBoxRepeat", "\uD83D\uDD02");
}
