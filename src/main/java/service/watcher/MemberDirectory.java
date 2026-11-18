package service.watcher;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.watcher.handlers.SseResponse;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-side cache of member display info for the watcher dashboard.
 * Cached or JDA-cached members are returned immediately; unknown ones are fetched
 * in the background (JDA handles the REST rate limits) and a "members" SSE event
 * tells the dashboard to reload once a guild's batch is done.
 */
public class MemberDirectory {
    private static final Logger logger = LoggerFactory.getLogger(MemberDirectory.class);
    private static final long TTL_MS = 12 * 60 * 60 * 1000L;
    public static final String WATCH_TYPE_MEMBERS = "members";

    private record Info(String name, String avatarUrl, long fetchedAt) {
    }

    private static final Map<String, Info> cache = new ConcurrentHashMap<>();
    private static final Set<String> inflight = ConcurrentHashMap.newKeySet();
    private static final Map<String, AtomicInteger> pendingByGuild = new ConcurrentHashMap<>();

    /**
     * Returns display info for the given users (userId -> {name, avatarUrl}).
     * Users without info yet are left out and fetched in the background.
     */
    public static Map<String, Map<String, String>> resolve(Guild guild, Collection<String> userIds) {
        Map<String, Map<String, String>> result = new HashMap<>();
        List<String> toFetch = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (String userId : userIds) {
            String key = key(guild.getId(), userId);
            Info info = cache.get(key);
            Member cached = guild.getMemberById(userId);
            if (cached != null) {
                info = of(cached, now);
                cache.put(key, info);
            }
            if (info == null || now - info.fetchedAt() > TTL_MS) toFetch.add(userId);
            if (info != null) result.put(userId, toMap(info));
        }

        if (!toFetch.isEmpty()) fetchAsync(guild, toFetch);
        return result;
    }

    private static void fetchAsync(Guild guild, List<String> userIds) {
        AtomicInteger pending = pendingByGuild.computeIfAbsent(guild.getId(), k -> new AtomicInteger());
        for (String userId : userIds) {
            String key = key(guild.getId(), userId);
            if (!inflight.add(key)) continue;
            pending.incrementAndGet();
            guild.retrieveMemberById(userId).queue(
                    member -> done(guild, key, of(member, System.currentTimeMillis())),
                    memberError -> guild.getJDA().retrieveUserById(userId).queue(
                            user -> done(guild, key, new Info(user.getEffectiveName(), user.getEffectiveAvatarUrl(), System.currentTimeMillis())),
                            userError -> done(guild, key, new Info(null, null, System.currentTimeMillis()))
                    )
            );
        }
    }

    private static void done(Guild guild, String key, Info info) {
        cache.put(key, info);
        inflight.remove(key);
        AtomicInteger pending = pendingByGuild.get(guild.getId());
        if (pending != null && pending.decrementAndGet() <= 0) {
            logger.info("Member info refreshed for guild {}", guild.getName());
            Watcher.sendData(new SseResponse(WATCH_TYPE_MEMBERS, guild.getId()));
        }
    }

    private static Info of(Member member, long now) {
        return new Info(member.getEffectiveName(), member.getEffectiveAvatarUrl(), now);
    }

    private static Map<String, String> toMap(Info info) {
        Map<String, String> map = new HashMap<>();
        map.put("name", info.name());
        map.put("avatarUrl", info.avatarUrl());
        return map;
    }

    private static String key(String guildId, String userId) {
        return guildId + ":" + userId;
    }
}
