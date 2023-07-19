package service.music.tools;

import net.dv8tion.jda.api.interactions.components.buttons.Button;
import service.inmemory.RedisClient;
import service.music.object.MusicBoxComponents;
import service.music.object.MusicPlayMode;

import java.time.Duration;

public class MusicUtil {
    public static Button getMusicRepeatModeButton(MusicPlayMode mode) {
        switch(mode) {
            case NORMAL: return MusicBoxComponents.MusicNormalModeButton;
            case REPEAT_ALL: return MusicBoxComponents.MusicRepeatAllModeButton;
            case REPEAT_SINGLE: return MusicBoxComponents.MusicRepeatSingleModeButton;
        }
        return MusicBoxComponents.MusicNormalModeButton;
    }

    public static String getMusicPlayModeDescription(MusicPlayMode musicPlayMode){
        switch(musicPlayMode){
            case NORMAL: return "기본";
            case REPEAT_ALL: return "전제 반복";
            case REPEAT_SINGLE: return "한 곡 반복";
        }
        return "기본";
    }

    public static String getDurationString(Duration d) {
        return String.format("%02d:%02d:%02d", d.toHours(), d.toMinutes() % 60, d.getSeconds() % 60);
    }

    public static String musicBoxMessageKey(String guildId) {
        return "guildMusicBoxMessage-" + guildId;
    }

    public static String musicChannelKey(String guildId) {
        return "guildMusicChannel-" + guildId;
    }
}