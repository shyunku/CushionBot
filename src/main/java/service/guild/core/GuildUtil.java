package service.guild.core;

public class GuildUtil {
    public static String musicBoxMessageKey(String guildId) {
        return "guildMusicBoxMessage-" + guildId;
    }

    public static String recruitChannelKey(String guildId) {
        return "guildRecruitChannel-" + guildId;
    }

    public static String musicChannelKey(String guildId) {
        return "guildMusicChannel-" + guildId;
    }

    public static String lolBoxMessageKey(String guildId) {
        return "guildLolBoxMessage-" + guildId;
    }

    public static String lolChannelKey(String guildId) {
        return "guildLolChannel-" + guildId;
    }
}
