package core;

import exceptions.GuildManagerNotFoundException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.guild.core.GuildManager;
import service.leagueoflegends.Core.LolBox;
import service.music.Core.MusicBox;
import service.recruit.RecruitManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Service {
    private static final Logger logger = LoggerFactory.getLogger(Service.class);
    // guild command router mapping <GuildId, GuildManager>
    public static final Map<String, GuildManager> guildManagers = new ConcurrentHashMap<>();

    public static void addGuildManagerIfNotExists(Guild guild) {
        guildManagers.computeIfAbsent(guild.getId(), id -> new GuildManager(guild));
    }

    /** The bot left the guild: stop its player and listeners and forget it. */
    public static void removeGuildManager(String guildId) {
        GuildManager guildManager = guildManagers.remove(guildId);
        if (guildManager != null) guildManager.destroy();
    }

    public static GuildManager getGuildManager(String guildId) throws GuildManagerNotFoundException {
        GuildManager guildManager = guildManagers.get(guildId);
        if (guildManager == null) throw new GuildManagerNotFoundException();
        return guildManager;
    }

    public static MusicBox GetMusicBoxByGuildId(String guildId) throws GuildManagerNotFoundException {
        return getGuildManager(guildId).getMusicBox();
    }

    public static LolBox GetLolBoxByGuildId(String guildId) throws GuildManagerNotFoundException {
        return getGuildManager(guildId).getLolBox();
    }

    public static RecruitManager GetRecruitManagerByGuildId(String guildId) throws GuildManagerNotFoundException {
        return getGuildManager(guildId).getRecruitManager();
    }

    /**
     * Remove every music and LoL box (waiting for Discord to confirm each delete), then stop the process.
     * The service manager starts it again in normal mode.
     */
    public static void finishMaintenance(JDA jda) {
        for (Guild guild : jda.getGuilds()) {
            GuildManager guildManager = guildManagers.get(guild.getId());
            if (guildManager == null) continue;
            try {
                guildManager.getMusicBox().clearEmbed();
                guildManager.getLolBox().clearEmbed();
            } catch (Exception ex) {
                logger.warn("Could not clear boxes in {}: {}", guild.getName(), ex.getMessage());
            }
        }
        System.exit(0);
    }
}
