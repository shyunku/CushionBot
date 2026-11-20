package service.music.Core;

import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Lavalink events are subscribed once for the whole client and routed to the streamer of the event's guild.
 * A handler that throws only loses that one event: before, the exception cancelled the subscription and
 * the guild stopped advancing to the next track until a restart.
 */
public final class MusicEvents {
    private static final Logger logger = LoggerFactory.getLogger(MusicEvents.class);
    private static final Map<Long, MusicStreamer> streamers = new ConcurrentHashMap<>();
    private static boolean nodeReadyOnce = false;

    private MusicEvents() {
    }

    public static void subscribe(LavalinkClient client) {
        client.on(TrackStartEvent.class).subscribe(e -> route(e.getGuildId(), s -> s.onTrackStart(e)), MusicEvents::onStreamError);
        client.on(TrackEndEvent.class).subscribe(e -> route(e.getGuildId(), s -> s.onTrackEnd(e)), MusicEvents::onStreamError);
        client.on(TrackExceptionEvent.class).subscribe(e -> route(e.getGuildId(), s -> s.onTrackException(e)), MusicEvents::onStreamError);
        client.on(TrackStuckEvent.class).subscribe(e -> route(e.getGuildId(), s -> s.onTrackStuck(e)), MusicEvents::onStreamError);
        client.on(ReadyEvent.class).subscribe(MusicEvents::onNodeReady, MusicEvents::onStreamError);
    }

    static void register(long guildId, MusicStreamer streamer) {
        streamers.put(guildId, streamer);
    }

    static void unregister(long guildId, MusicStreamer streamer) {
        streamers.remove(guildId, streamer);
    }

    private static void route(long guildId, Consumer<MusicStreamer> handler) {
        MusicStreamer streamer = streamers.get(guildId);
        if (streamer == null) return;
        try {
            handler.accept(streamer);
        } catch (Exception e) {
            logger.error("Music event handler failed for guild {}", guildId, e);
        }
    }

    /** A node that comes back without resuming has lost every player; drop the stale queue state. */
    private static synchronized void onNodeReady(ReadyEvent e) {
        if (!nodeReadyOnce) {
            nodeReadyOnce = true;
            return;
        }
        if (e.getResumed()) return;
        logger.warn("Lavalink node reconnected without resuming; resetting {} music players", streamers.size());
        for (MusicStreamer streamer : streamers.values()) {
            try {
                streamer.onNodeLost();
            } catch (Exception ex) {
                logger.error("Could not reset music player", ex);
            }
        }
    }

    private static void onStreamError(Throwable t) {
        logger.error("Lavalink event stream error", t);
    }
}
