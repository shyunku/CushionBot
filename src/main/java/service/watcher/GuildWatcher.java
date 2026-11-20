package service.watcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import core.CushionBot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import service.watcher.handlers.SseResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Voice access log: one list of sessions per (guild, user), saved to datafiles/access_logs.json.
 * Every read and write of {@link #accessSessions} happens under the class lock.
 */
public class GuildWatcher {
    private static final Logger logger = LoggerFactory.getLogger(GuildWatcher.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final long RETENTION_MS = 365L * 24 * 60 * 60 * 1000;
    /** Saves are batched: a change is written at most this long after it happened. */
    private static final long SAVE_DELAY_MS = 2000;
    /** The backup copy is refreshed at most this often. */
    private static final long BACKUP_INTERVAL_MS = 60 * 60 * 1000;

    private static final Map<String, Map<String, List<AccessSession>>> accessSessions = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService saver = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "access-log-saver");
        t.setDaemon(true);
        return t;
    });
    private static boolean saveScheduled = false;
    /** Last-modified time of the file the log was loaded from (0 when nothing was loaded). */
    private static long loadedModified = 0;
    private static volatile boolean initialized = false;

    private static Path dataFile() {
        return Path.of(System.getProperty("user.dir"), "datafiles", "access_logs.json");
    }

    private static Path backupFile() {
        return Path.of(System.getProperty("user.dir"), "datafiles", "access_logs.json.bak");
    }

    /** True once the saved log is loaded and members already in voice are recorded. */
    public static boolean isInitialized() {
        return initialized;
    }

    public static synchronized void addAccessLog(AccessType accessType, String guildId, String userId, String channelId) {
        long eventTime = System.currentTimeMillis();

        Map<String, List<AccessSession>> guildSessions = accessSessions.computeIfAbsent(guildId, k -> new HashMap<>());
        if (accessType == AccessType.LEAVE && !guildSessions.containsKey(userId)) {
            logger.info("Ignoring leave of {} in guild {}: no session recorded", userId, guildId);
            return;
        }
        List<AccessSession> sessions = guildSessions.computeIfAbsent(userId, k -> new ArrayList<>());
        removeUnlastUncompleteSession(sessions);
        removeOldSessions(sessions);
        AccessSession lastSession = getLastSession(sessions);
        boolean open = lastSession != null && !lastSession.isComplete();

        switch (accessType) {
            case JOIN:
                // a join while a session is still open means the leave was missed; end the old one now
                if (open) lastSession.setLeaveTime(eventTime);
                sessions.add(new AccessSession(userId, channelId, eventTime));
                break;
            case MOVED:
                if (open) lastSession.setLeaveTime(eventTime);
                sessions.add(new AccessSession(userId, channelId, eventTime));
                break;
            case LEAVE:
                // without an open session the join time is unknown; nothing reliable to record
                if (open) lastSession.setLeaveTime(eventTime);
                else logger.info("Ignoring leave of {} in guild {}: no open session", userId, guildId);
                break;
            default:
                return;
        }

        requestSave();
        Watcher.sendData(new SseResponse(Watcher.WATCH_TYPE_CHANGE, guildId));
    }

    private static void removeUnlastUncompleteSession(List<AccessSession> sessions) {
        if (sessions.isEmpty()) return;
        AccessSession lastSession = getLastSession(sessions);
        sessions.removeIf(session -> session != lastSession && !session.isComplete());
    }

    private static void removeOldSessions(List<AccessSession> sessions) {
        long currentTime = System.currentTimeMillis();
        sessions.removeIf(session -> session.isComplete() && currentTime - session.getJoinTime() > RETENTION_MS);
    }

    private static AccessSession getLastSession(List<AccessSession> sessions) {
        if (sessions.isEmpty()) return null;
        sessions.sort(Comparator.comparingLong(AccessSession::getJoinTime));
        return sessions.get(sessions.size() - 1);
    }

    public static synchronized void initialize() {
        Map<String, Map<String, List<AccessSession>>> loaded = load();
        long lastSaved = loadedModified;
        accessSessions.clear();
        for (Map.Entry<String, Map<String, List<AccessSession>>> guildEntry : loaded.entrySet()) {
            Map<String, List<AccessSession>> users = new HashMap<>();
            guildEntry.getValue().forEach((userId, sessions) -> users.put(userId, new ArrayList<>(sessions)));
            accessSessions.put(guildEntry.getKey(), users);
        }

        // Sessions still open in the file were cut off when the bot stopped. The file is saved shortly after
        // every voice event, so its last-modified time is the latest moment the bot is known to have been
        // recording; close them there. Members still in voice get a new session from now (below).
        int closed = 0;
        for (Map<String, List<AccessSession>> users : accessSessions.values()) {
            for (List<AccessSession> sessions : users.values()) {
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

        for (Guild guild : CushionBot.jda.getGuilds()) {
            syncGuild(guild);
        }
        requestSave();
        if (!initialized) {
            // flush pending changes when the bot is stopped
            Runtime.getRuntime().addShutdownHook(new Thread(GuildWatcher::saveNow, "access-log-final-save"));
        }
        initialized = true;
        // dashboards that loaded while the bot was starting reload now
        Watcher.sendData(new SseResponse(Watcher.WATCH_TYPE_RESYNC, null));
    }

    /** Record members who are in voice right now (also used when the bot joins a guild). */
    public static synchronized void syncGuild(Guild guild) {
        String guildId = guild.getId();
        Map<String, List<AccessSession>> users = accessSessions.computeIfAbsent(guildId, k -> new HashMap<>());
        Map<String, String> inVoice = new HashMap<>();
        for (GuildVoiceState state : guild.getVoiceStates()) {
            AudioChannel channel = state.getChannel();
            if (channel == null) continue;
            inVoice.put(state.getMember().getId(), channel.getId());
        }
        inVoice.forEach((userId, channelId) -> {
            AccessSession last = getLastSession(users.computeIfAbsent(userId, k -> new ArrayList<>()));
            if (last == null || last.isComplete()) {
                addAccessLog(AccessType.JOIN, guildId, userId, channelId);
            } else if (!last.getChannelId().equals(channelId)) {
                addAccessLog(AccessType.MOVED, guildId, userId, channelId);
            }
        });
        for (Map.Entry<String, List<AccessSession>> entry : users.entrySet()) {
            AccessSession last = getLastSession(entry.getValue());
            if (last != null && !last.isComplete() && !inVoice.containsKey(entry.getKey())) {
                addAccessLog(AccessType.LEAVE, guildId, entry.getKey(), last.getChannelId());
            }
        }
        MemberDirectory.resolve(guild, users.keySet());
    }

    /**
     * Read the saved log. A file that cannot be parsed is moved aside (never overwritten) and the backup is
     * tried next, so a crash mid-write can no longer wipe the history.
     */
    private static Map<String, Map<String, List<AccessSession>>> load() {
        File dir = dataFile().getParent().toFile();
        if (!dir.exists() && !dir.mkdirs()) {
            logger.error("Could not create {}", dir);
        }
        TypeReference<Map<String, Map<String, List<AccessSession>>>> type = new TypeReference<>() {
        };
        for (Path path : List.of(dataFile(), backupFile())) {
            if (!Files.exists(path)) continue;
            try {
                Map<String, Map<String, List<AccessSession>>> data = mapper.readValue(path.toFile(), type);
                loadedModified = Files.getLastModifiedTime(path).toMillis();
                if (!path.equals(dataFile())) logger.warn("Loaded access log from backup {}", path);
                return data;
            } catch (IOException e) {
                Path aside = path.resolveSibling(path.getFileName() + ".broken-" + System.currentTimeMillis());
                logger.error("Could not read {}; moving it to {}", path, aside, e);
                try {
                    Files.move(path, aside);
                } catch (IOException moveError) {
                    logger.error("Could not move {} aside", path, moveError);
                }
            }
        }
        return new HashMap<>();
    }

    private static synchronized void requestSave() {
        if (saveScheduled) return;
        saveScheduled = true;
        saver.schedule(GuildWatcher::saveNow, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /** Write the log atomically: a temp file is written and synced, then moved over the old file. */
    public static void saveNow() {
        byte[] json;
        synchronized (GuildWatcher.class) {
            saveScheduled = false;
            try {
                json = mapper.writeValueAsBytes(accessSessions);
            } catch (IOException e) {
                logger.error("Could not serialize access log", e);
                return;
            }
        }
        Path target = dataFile();
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temp.toFile())) {
                out.write(json);
                out.getFD().sync();
            }
            Path backup = backupFile();
            if (Files.exists(target) && (!Files.exists(backup)
                    || System.currentTimeMillis() - Files.getLastModifiedTime(backup).toMillis() > BACKUP_INTERVAL_MS)) {
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.error("Could not save access log", e);
        }
    }

    /** Total connected time per user in a guild over the whole kept record (the dashboard's "누적"). */
    public static synchronized Map<String, Long> totals(String guildId) {
        Map<String, Long> result = new HashMap<>();
        Map<String, List<AccessSession>> users = accessSessions.get(guildId);
        if (users == null) return result;
        users.forEach((userId, sessions) -> {
            long total = 0;
            for (AccessSession session : sessions) {
                if (session.getJoinTime() == 0) continue;
                total += session.getDuration();
            }
            if (total > 0) result.put(userId, total);
        });
        return result;
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
     * Everything the dashboard needs for one guild in a single response: guild info, member display info,
     * channel names by id, and every recorded session (channel id; leave 0 = still connected).
     */
    public static synchronized Map<String, Object> snapshot(Guild guild) {
        Map<String, List<AccessSession>> guildSessions = accessSessions.getOrDefault(guild.getId(), Collections.emptyMap());
        Map<String, String> channelNames = new HashMap<>();
        Map<String, List<Map<String, Object>>> sessions = new HashMap<>();

        for (Map.Entry<String, List<AccessSession>> userEntry : guildSessions.entrySet()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (AccessSession session : userEntry.getValue()) {
                if (session.getJoinTime() == 0) continue;
                String channelId = session.getChannelId() == null ? "unknown" : session.getChannelId();
                channelNames.computeIfAbsent(channelId, id -> {
                    GuildChannel channel = guild.getGuildChannelById(id);
                    return channel == null ? null : channel.getName();
                });
                Map<String, Object> item = new HashMap<>();
                item.put("channel", channelId);
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
        // deleted channels map to null; the dashboard shows them as "삭제된 채널"
        result.put("channels", channelNames);
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
