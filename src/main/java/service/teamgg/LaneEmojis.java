package service.teamgg;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.emoji.ApplicationEmoji;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application emojis for the team.gg lane panel: lane icons, preference dots and mastery squares
 * (src/main/resources/emojis/*.png). Missing ones are uploaded at startup; until they are there the
 * panel falls back to plain characters.
 */
public class LaneEmojis {
    private static final Logger logger = LoggerFactory.getLogger(LaneEmojis.class);

    public static final List<String> NAMES = List.of(
            "lane_top", "lane_jungle", "lane_mid", "lane_bottom", "lane_support",
            "pref_dislike", "pref_normal", "pref_like", "pref_main",
            "mast_0", "mast_1", "mast_2", "mast_3", "mast_4", "mast_5");

    /** name -> "<:name:id>" */
    private static final Map<String, String> mentions = new ConcurrentHashMap<>();

    public static void load(JDA jda) {
        jda.retrieveApplicationEmojis().queue(existing -> {
            for (ApplicationEmoji emoji : existing) {
                if (NAMES.contains(emoji.getName())) mentions.put(emoji.getName(), emoji.getAsMention());
            }
            for (String name : NAMES) {
                if (mentions.containsKey(name)) continue;
                byte[] png = read(name);
                if (png == null) {
                    logger.warn("Emoji image {}.png not found", name);
                    continue;
                }
                jda.createApplicationEmoji(name, Icon.from(png, Icon.IconType.PNG)).queue(
                        emoji -> {
                            mentions.put(name, emoji.getAsMention());
                            logger.info("Uploaded application emoji {}", name);
                        },
                        err -> logger.warn("Uploading application emoji {} failed: {}", name, err.getMessage()));
            }
        }, err -> logger.warn("Listing application emojis failed: {}", err.getMessage()));
    }

    private static byte[] read(String name) {
        try (InputStream in = LaneEmojis.class.getResourceAsStream("/emojis/" + name + ".png")) {
            if (in != null) return in.readAllBytes();
        } catch (Exception ignored) {
        }
        try {
            File file = new File("src/main/resources/emojis/" + name + ".png");
            if (file.isFile()) return Files.readAllBytes(file.toPath());
        } catch (Exception ignored) {
        }
        return null;
    }

    /** The emoji's mention, or the fallback text when it is not available. */
    public static String get(String name, String fallback) {
        String mention = mentions.get(name);
        return mention == null ? fallback : mention;
    }
}
