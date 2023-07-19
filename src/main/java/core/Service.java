package core;

import exceptions.MusicBoxNotFoundException;
import net.dv8tion.jda.api.entities.Guild;
import service.guild.GuildManager;
import service.music.Core.MusicBox;

import java.util.HashMap;

public class Service {
    // guild command router mapping <GuildId, GuildManager>
    public static HashMap<String, GuildManager> guildManagers = new HashMap<>();

    public static void addGuildManagerIfNotExists(Guild guild) {
        String guildId = guild.getId();
        if(!guildManagers.containsKey(guildId)) {
            GuildManager newGuildManager = new GuildManager(guild);
            guildManagers.put(guildId, newGuildManager);
        }
    }

    public static MusicBox GetMusicBoxByGuildId(String guildId) throws MusicBoxNotFoundException {
        if(!guildManagers.containsKey(guildId)) {
            throw new MusicBoxNotFoundException();
        }
        GuildManager guildManager = guildManagers.get(guildId);
        return guildManager.getMusicBox();
    }
}
