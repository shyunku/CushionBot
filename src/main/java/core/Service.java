package core;

import exceptions.GuildManagerNotFoundException;
import net.dv8tion.jda.api.entities.Guild;
import service.guild.core.GuildManager;
import service.leagueoflegends.Core.LolBox;
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

    public static MusicBox GetMusicBoxByGuildId(String guildId) throws GuildManagerNotFoundException {
        if(!guildManagers.containsKey(guildId)) throw new GuildManagerNotFoundException();
        GuildManager guildManager = guildManagers.get(guildId);
        return guildManager.getMusicBox();
    }

    public static LolBox GetLolBoxByGuildId(String guildId) throws GuildManagerNotFoundException {
        if(!guildManagers.containsKey(guildId)) throw new GuildManagerNotFoundException();
        GuildManager guildManager = guildManagers.get(guildId);
        return guildManager.getLolBox();
    }
}
