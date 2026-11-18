package service.watcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import core.CushionBot;
import core.InternalEventListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import service.watcher.handlers.SseResponse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuildWatcher {
    private static long initializeTime;
    private static final Logger logger = LoggerFactory.getLogger(GuildWatcher.class);
    public static Map<String, Map<String, List<AccessSession>>> accessSessions = new ConcurrentHashMap<>();

    public static synchronized void addAccessLog(AccessType accessType, String guildId, String userId, String channelId) {
        long eventTime = System.currentTimeMillis();

        Map<String, List<AccessSession>> guildSessions = accessSessions.computeIfAbsent(guildId, k -> new HashMap<>());
        List<AccessSession> sessions = guildSessions.computeIfAbsent(userId, k -> new ArrayList<>());
        AccessSession lastSession = getLastSession(sessions);
        removeUnlastUncompleteSession(sessions);
        removeOldSessions(sessions);
        if (lastSession != null && !lastSession.isComplete()) {
            switch (accessType) {
                case JOIN:
                    sessions.add(new AccessSession(userId, channelId, eventTime));
                    break;
                case MOVED:
                    lastSession.setLeaveTime(eventTime);
                    sessions.add(new AccessSession(userId, channelId, eventTime));
                    break;
                case LEAVE:
                    lastSession.setLeaveTime(eventTime);
                    break;
            }
        } else {
            switch (accessType) {
                case JOIN:
                    sessions.add(new AccessSession(userId, channelId, eventTime));
                    break;
                case MOVED:
                case LEAVE:
                    sessions.add(new AccessSession(userId, channelId, initializeTime, eventTime));
                    break;
            }
        }

        saveAll();
        Watcher.sendData(new SseResponse(Watcher.WATCH_TYPE_CHANGE, guildId));
    }

    private static void removeUnlastUncompleteSession(List<AccessSession> sessions) {
        if (sessions.isEmpty()) return;
        AccessSession lastSession = getLastSession(sessions);
        sessions.removeIf(session -> session != lastSession && !session.isComplete());
    }

    private static void removeOldSessions(List<AccessSession> sessions) {
        long currentTime = System.currentTimeMillis();
        sessions.removeIf(session -> session.isComplete() && currentTime - session.getJoinTime() > 365 * 24 * 60 * 60 * 1000L);
    }

    private static AccessSession getLastSession(List<AccessSession> sessions) {
        if (sessions.isEmpty()) return null;
        sessions.sort(Comparator.comparingLong(AccessSession::getJoinTime));
        return sessions.get(sessions.size() - 1);
    }

    public static synchronized void initialize() {
        initializeTime = System.currentTimeMillis();

        // load access logs from file
        String dirpath = System.getProperty("user.dir") + "/datafiles";
        String filepath = dirpath + "/access_logs.json";
        File dir = new File(dirpath);
        if (!dir.exists()) {
            dir.mkdir();
            return;
        }

        File file = new File(filepath);
        if (!file.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            ObjectMapper mapper = new ObjectMapper();
            TypeReference<Map<String, Map<String, List<AccessSession>>>> typeRef = new TypeReference<Map<String, Map<String, List<AccessSession>>>>() {
            };
            accessSessions = mapper.readValue(json.toString(), typeRef);

            // Sessions still open in the file were cut off when the bot stopped. The file is saved on every
            // voice event, so its last-modified time is the latest moment the bot is known to have been
            // recording; close them there. Members still in voice get a new session from now (below).
            long lastSaved = file.lastModified();
            int closed = 0;
            for (Map.Entry<String, Map<String, List<AccessSession>>> guildEntry : accessSessions.entrySet()) {
                for (Map.Entry<String, List<AccessSession>> userEntry : guildEntry.getValue().entrySet()) {
                    List<AccessSession> sessions = userEntry.getValue();
                    removeOldSessions(sessions);
                    removeUnlastUncompleteSession(sessions);
                    AccessSession lastSession = getLastSession(sessions);
                    if (lastSession != null && !lastSession.isComplete()) {
                        lastSession.setLeaveTime(Math.max(lastSession.getJoinTime(), lastSaved));
                        closed++;
                    }
                }
            }
            if (closed > 0) {
                logger.info("Closed {} sessions left open by the previous run at {}", closed, new Date(lastSaved));
            }

            JDA jda = CushionBot.jda;
            List<Guild> guilds = jda.getGuilds();
            for (Guild guild : guilds) {
                String guildId = guild.getId();
                if (!accessSessions.containsKey(guildId)) {
                    accessSessions.put(guildId, new HashMap<>());
                }

                Map<String, String> joiners = new HashMap<>();

                // already joined members
                guild.getVoiceChannels().forEach(voiceChannel -> {
                    voiceChannel.getMembers().forEach(member -> {
                        joiners.put(member.getId(), voiceChannel.getId());
                        List<AccessSession> userSessions = accessSessions.get(guildId).computeIfAbsent(member.getId(), k -> new ArrayList<>());
                        AccessSession lastSession = getLastSession(userSessions);
                        if (lastSession == null || lastSession.isComplete()) {
                            logger.info("Member {} already joined voice channel {} in guild {}", member.getUser().getName(), voiceChannel.getName(), guild.getName());
                            addAccessLog(AccessType.JOIN, guildId, member.getId(), voiceChannel.getId());
                        }
                    });
                });

                // left members
                accessSessions.get(guildId).forEach((userId, sessions) -> {
                    AccessSession lastSession = getLastSession(sessions);
                    if (!joiners.containsKey(userId)) {
                        // disconnected
                        if (lastSession != null && !lastSession.isComplete()) {
                            logger.info("Member {} left voice channel {} in guild {}", userId, lastSession.getChannelId(), guild.getName());
                            addAccessLog(AccessType.LEAVE, guildId, userId, lastSession.getChannelId());
                        }
                    } else {
                        // connecting
                        String voiceChannelId = joiners.get(userId);
                        if (lastSession == null || lastSession.isComplete()) {
                            logger.info("Member {} joined voice channel {} in guild {}", userId, voiceChannelId, guild.getName());
                            addAccessLog(AccessType.JOIN, guildId, userId, voiceChannelId);
                        } else if (!lastSession.isComplete() && !lastSession.getChannelId().equals(voiceChannelId)) {
                            logger.info("Member {} moved from voice channel {} to {} in guild {}", userId, lastSession.getChannelId(), voiceChannelId, guild.getName());
                            addAccessLog(AccessType.MOVED, guildId, userId, voiceChannelId);
                        }
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // warm the dashboard member cache in the background
        for (Guild guild : CushionBot.jda.getGuilds()) {
            Map<String, List<AccessSession>> guildSessions = accessSessions.get(guild.getId());
            if (guildSessions != null) MemberDirectory.resolve(guild, guildSessions.keySet());
        }
    }

    public static void saveAll() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
            String json = writer.writeValueAsString(accessSessions);

            // check if guild exists
            for (Map.Entry<String, Map<String, List<AccessSession>>> guildEntry : accessSessions.entrySet()) {
                Guild guild = CushionBot.jda.getGuildById(guildEntry.getKey());
                if (guild == null) {
                    accessSessions.remove(guildEntry.getKey());

                }
            }

            String dirpath = System.getProperty("user.dir") + "/datafiles";
            String filepath = dirpath + "/access_logs.json";

            // write
            try (FileWriter fwriter = new FileWriter(filepath)) {
                fwriter.write(json);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Guilds shown in the dashboard, with the number of people connected right now.
     */
    public static synchronized List<Map<String, Object>> guildList() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Guild guild : CushionBot.jda.getGuilds()) {
            Map<String, List<AccessSession>> guildSessions = accessSessions.get(guild.getId());
            if (guildSessions == null) continue;
            int online = 0;
            for (List<AccessSession> sessions : guildSessions.values()) {
                AccessSession last = getLastSession(sessions);
                if (last != null && !last.isComplete()) online++;
            }
            Map<String, Object> info = guildInfo(guild);
            info.put("online", online);
            result.add(info);
        }
        return result;
    }

    /**
     * Everything the dashboard needs for one guild in a single response:
     * guild info, member display info and every recorded session (leave 0 = still connected).
     */
    public static synchronized Map<String, Object> snapshot(Guild guild) {
        Map<String, List<AccessSession>> guildSessions = accessSessions.getOrDefault(guild.getId(), Collections.emptyMap());
        Map<String, String> channelNames = new HashMap<>();
        Map<String, List<Map<String, Object>>> sessions = new HashMap<>();

        for (Map.Entry<String, List<AccessSession>> userEntry : guildSessions.entrySet()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (AccessSession session : userEntry.getValue()) {
                if (session.getJoinTime() == 0) continue;
                String channelName = channelNames.computeIfAbsent(session.getChannelId(), id -> {
                    GuildChannel channel = id == null ? null : guild.getGuildChannelById(id);
                    return channel == null ? "알 수 없는 채널" : channel.getName();
                });
                Map<String, Object> item = new HashMap<>();
                item.put("channel", channelName);
                item.put("join", session.getJoinTime());
                item.put("leave", session.getLeaveTime());
                list.add(item);
            }
            list.sort(Comparator.comparingLong(item -> (long) item.get("join")));
            sessions.put(userEntry.getKey(), list);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("guild", guildInfo(guild));
        result.put("serverTime", System.currentTimeMillis());
        result.put("members", MemberDirectory.resolve(guild, sessions.keySet()));
        result.put("sessions", sessions);
        return result;
    }

    private static Map<String, Object> guildInfo(Guild guild) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", guild.getId());
        info.put("name", guild.getName());
        info.put("iconUrl", guild.getIconUrl());
        info.put("memberCount", guild.getMemberCount());
        return info;
    }
}
