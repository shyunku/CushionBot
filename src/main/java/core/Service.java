package core;

import exceptions.GuildManagerNotFoundException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.Event;
import service.guild.core.GuildManager;
import service.leagueoflegends.Core.LolBox;
import service.music.Core.MusicBox;
import service.recruit.RecruitManager;

import java.util.HashMap;
import java.util.List;

public class Service {
    // guild command router mapping <GuildId, GuildManager>
    public static HashMap<String, GuildManager> guildManagers = new HashMap<>();

    public static void addGuildManagerIfNotExists(Guild guild) {
        String guildId = guild.getId();
        if (!guildManagers.containsKey(guildId)) {
            GuildManager newGuildManager = new GuildManager(guild);
            guildManagers.put(guildId, newGuildManager);
        }
    }

    public static GuildManager getGuildManager(String guildId) throws GuildManagerNotFoundException {
        if (!guildManagers.containsKey(guildId)) throw new GuildManagerNotFoundException();
        return guildManagers.get(guildId);
    }

    public static MusicBox GetMusicBoxByGuildId(String guildId) throws GuildManagerNotFoundException {
        if (!guildManagers.containsKey(guildId)) throw new GuildManagerNotFoundException();
        GuildManager guildManager = guildManagers.get(guildId);
        return guildManager.getMusicBox();
    }

    public static LolBox GetLolBoxByGuildId(String guildId) throws GuildManagerNotFoundException {
        if (!guildManagers.containsKey(guildId)) throw new GuildManagerNotFoundException();
        GuildManager guildManager = guildManagers.get(guildId);
        return guildManager.getLolBox();
    }

    public static RecruitManager GetRecruitManagerByGuildId(String guildId) throws GuildManagerNotFoundException {
        if (!guildManagers.containsKey(guildId)) throw new GuildManagerNotFoundException();
        GuildManager guildManager = guildManagers.get(guildId);
        return guildManager.getRecruitManager();
    }

    public static void finishMaintenance(Event e) {
        // clear all music box and lol box embed
        JDA jda = e.getJDA();
        List<Guild> guilds = jda.getGuilds();
        for (Guild guild : guilds) {
            Service.addGuildManagerIfNotExists(guild);
            try {
                GuildManager guildManager = Service.getGuildManager(guild.getId());
                guildManager.getMusicBox().clearEmbed();
                guildManager.getLolBox().clearEmbed();
            } catch (GuildManagerNotFoundException ex) {
            }
        }
        System.exit(0);
    }
}
