package service.guild.core;

import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.leagueoflegends.Core.LolBox;
import service.music.Core.MusicBox;

public class GuildManager {
    private final Logger logger = LoggerFactory.getLogger(GuildManager.class);
    private Guild guild;
    private GuildCommandRouter guildCommandRouter;
    private MusicBox musicBox;
    private LolBox lolBox;

    public GuildManager(Guild guild) {
        this.guild = guild;
        this.guildCommandRouter = new GuildCommandRouter(guild);
        this.musicBox = new MusicBox(guild, null);
        this.lolBox = new LolBox(guild, null);
    }

    public GuildCommandRouter getGuildCommandRouter() {
        return guildCommandRouter;
    }

    public MusicBox getMusicBox() {
        return musicBox;
    }

    public LolBox getLolBox() {
        return lolBox;
    }
}
