package service.watcher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import core.CushionBot;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import service.watcher.handlers.SseResponse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GuildWatcher {
    private static long initializeTime;
    public static Map<String, Map<String, List<AccessSession>>> accessSessions = new ConcurrentHashMap<>();

    public static void addAccessLog(AccessType accessType, String guildId, String userId, String channelId) {
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
        Watcher.sendData(new SseResponse(Watcher.WATCH_TYPE_CHANGE, null));
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

    public static void initialize() {
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

            for (Map.Entry<String, Map<String, List<AccessSession>>> guildEntry : accessSessions.entrySet()) {
                for (Map.Entry<String, List<AccessSession>> userEntry : guildEntry.getValue().entrySet()) {
                    List<AccessSession> sessions = userEntry.getValue();
                    removeOldSessions(sessions);
                    removeUnlastUncompleteSession(sessions);
                }
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
                            addAccessLog(AccessType.LEAVE, guildId, userId, lastSession.getChannelId());
                        }
                    } else {
                        // connecting
                        String voiceChannelId = joiners.get(userId);
                        if (lastSession == null || lastSession.isComplete()) {
                            addAccessLog(AccessType.JOIN, guildId, userId, voiceChannelId);
                        } else if (!lastSession.isComplete() && !lastSession.getChannelId().equals(voiceChannelId)) {
                            addAccessLog(AccessType.MOVED, guildId, userId, voiceChannelId);
                        }
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveAll() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
            String json = writer.writeValueAsString(accessSessions);
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

    public static String getAccessLogs() throws JsonProcessingException {
        JDA jda = CushionBot.jda;
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Map<String, List<DisplayableAccessSession>>> displayableSessions = new HashMap<>();
        for (Map.Entry<String, Map<String, List<AccessSession>>> guildEntry : accessSessions.entrySet()) {
            Map<String, List<DisplayableAccessSession>> guildDisplayableSessions = new HashMap<>();
            for (Map.Entry<String, List<AccessSession>> userEntry : guildEntry.getValue().entrySet()) {
                List<DisplayableAccessSession> displayableUserSessions = new ArrayList<>();
                for (AccessSession session : userEntry.getValue()) {
                    try {
                        DisplayableAccessSession displayableSession = session.toDisplayable(jda, guildEntry.getKey());
                        displayableUserSessions.add(displayableSession);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                guildDisplayableSessions.put(userEntry.getKey(), displayableUserSessions);
            }
            displayableSessions.put(guildEntry.getKey(), guildDisplayableSessions);
        }
        return mapper.writeValueAsString(displayableSessions);
    }
}
