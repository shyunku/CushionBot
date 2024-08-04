package service.watcher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import core.CushionBot;
import net.dv8tion.jda.api.JDA;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GuildWatcher {
    private static long initializeTime;
    public static Map<String, Map<String, List<AccessSession>>> accessSessions = new ConcurrentHashMap<>();

    public static void addAccessLog(AccessType accessType, String guildId, String userId, String channelId) {
        long eventTime = System.currentTimeMillis();

        Map<String, List<AccessSession>> guildSessions = accessSessions.computeIfAbsent(guildId, k -> new HashMap<>());
        List<AccessSession> sessions = guildSessions.computeIfAbsent(userId, k -> new ArrayList<>());
        AccessSession lastSession = getLastSession(sessions);
        if (lastSession != null && !lastSession.isComplete()) {
            switch (accessType) {
                case JOIN:
                    // remove incomplete session
                    sessions.remove(lastSession);
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
    }

    private static AccessSession getLastSession(List<AccessSession> sessions) {
        if (sessions.isEmpty()) return null;
        return sessions.get(sessions.size() - 1);
    }

    public static void loadAll() {
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
