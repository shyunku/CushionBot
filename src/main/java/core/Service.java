package core;

import core.command.GuildCommandRouter;

import java.util.HashMap;

public class Service {
    // music channel mapping <GuildId, TextChannelId>
    public static HashMap<String, String> guildMusicChannelMap = new HashMap<>();
    // guild command router mapping <GuildId, GuildCommandRouter>
    public static HashMap<String, GuildCommandRouter> guildCommandRouters = new HashMap<>();
}
