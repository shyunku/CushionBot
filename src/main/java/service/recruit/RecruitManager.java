package service.recruit;

import core.CushionBot;
import exceptions.RecruitPublishException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.guild.core.GuildUtil;
import service.inmemory.RedisClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RecruitManager extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(RecruitManager.class);
    /** How far back to look for this bot's leftover recruit posts after a restart. */
    private static final int CLEANUP_HISTORY = 200;
    private static final Emoji CHECK = Emoji.fromUnicode("✅");

    private final String guildId;
    /** registerer user id -> recruit key -> recruit */
    private final Map<String, Map<String, Recruit>> recruits = new ConcurrentHashMap<>();
    public volatile TextChannel recruitChannel;

    public RecruitManager(Guild guild) {
        this.guildId = guild.getId();

        // get recruit channel from database
        String recruitChannelKey = GuildUtil.recruitChannelKey(guild.getId());
        if (RedisClient.has(recruitChannelKey)) {
            String channelId = RedisClient.get(recruitChannelKey);
            recruitChannel = guild.getTextChannelById(channelId);
            if (recruitChannel == null) {
                logger.info("Recruit channel not found. Clearing recruit channel from database.");
                RedisClient.del(recruitChannelKey);
            }
        }

        if (recruitChannel != null) {
            // recruits do not survive a restart: remove this bot's own leftover posts, never other messages
            String selfId = guild.getSelfMember().getId();
            recruitChannel.getIterableHistory().takeAsync(CLEANUP_HISTORY).thenAccept(messages -> messages.stream()
                    .filter(message -> message.getAuthor().getId().equals(selfId))
                    .forEach(message -> message.delete().queue(null, failure -> {
                    }))).exceptionally(err -> {
                logger.warn("Could not clean up recruit channel: {}", err.getMessage());
                return null;
            });
        }

        CushionBot.jda.addEventListener(this);
    }

    public void registerRecruit(Member registerer, Recruit recruit) throws RecruitPublishException {
        TextChannel channel = recruitChannel;
        boolean mentionEveryone = channel != null && registerer.hasPermission(channel, Permission.MESSAGE_MENTION_EVERYONE);
        recruit.setOnDead(this::forget);
        // only keep recruits that were actually posted
        recruit.publish(channel, registerer, mentionEveryone);
        recruits.computeIfAbsent(registerer.getId(), k -> new ConcurrentHashMap<>()).put(recruit.getKey(), recruit);
    }

    private void forget(Recruit recruit) {
        for (Map<String, Recruit> userRecruits : recruits.values()) {
            userRecruits.remove(recruit.getKey(), recruit);
        }
        recruits.values().removeIf(Map::isEmpty);
    }

    public boolean unregisterRecruit(Member registerer, String key) {
        Map<String, Recruit> userRecruits = recruits.get(registerer.getId());
        if (userRecruits == null || userRecruits.isEmpty()) {
            return false;
        }

        if (key == null) {
            for (Recruit recruit : userRecruits.values().toArray(new Recruit[0])) {
                recruit.destroy();
            }
        } else {
            Recruit recruit = userRecruits.get(key);
            if (recruit == null) {
                logger.warn("Recruit not found for key: {}", key);
                return false;
            }
            recruit.destroy();
        }
        return true;
    }

    public Recruit getRecruit(String key) {
        for (Map<String, Recruit> recruitMap : recruits.values()) {
            Recruit recruit = recruitMap.get(key);
            if (recruit != null && !recruit.isDead) {
                return recruit;
            }
        }
        return null;
    }

    public boolean changeRecruitTime(Member registerer, String key, long newRecruitAt) {
        Recruit recruit = ownRecruit(registerer, key);
        if (recruit == null) return false;
        recruit.setRecruitAt(newRecruitAt);
        return true;
    }

    public boolean changeRecruitNum(Member registerer, String key, int newRecruitNum) {
        Recruit recruit = ownRecruit(registerer, key);
        if (recruit == null) return false;
        recruit.setRecruitNum(newRecruitNum);
        return true;
    }

    private Recruit ownRecruit(Member registerer, String key) {
        Map<String, Recruit> userRecruits = recruits.get(registerer.getId());
        Recruit recruit = userRecruits == null ? null : userRecruits.get(key);
        if (recruit == null || recruit.isDead) {
            logger.warn("Recruit not found for key: {}", key);
            return null;
        }
        return recruit;
    }

    private Recruit findByMessage(String messageId) {
        for (Map<String, Recruit> recruitMap : recruits.values()) {
            for (Recruit recruit : recruitMap.values()) {
                if (messageId.equals(recruit.getMessageId())) return recruit;
            }
        }
        return null;
    }

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        if (!event.isFromGuild() || !event.getGuild().getId().equals(guildId)) return;
        if (!event.getEmoji().equals(CHECK)) return;
        String userId = event.getUserId();
        if (userId.equals(event.getJDA().getSelfUser().getId())) return;
        if (event.getMember() != null && event.getMember().getUser().isBot()) return;

        Recruit recruit = findByMessage(event.getMessageId());
        if (recruit == null) return;
        if (!recruit.addParticipant(userId)) {
            event.retrieveUser().flatMap(user -> event.getReaction().removeReaction(user)).queue(null, failure -> {
            });
            logger.info("Reaction add blocked for {} by {}", recruit.getKey(), userId);
        } else {
            logger.info("Reaction added to {} by {}", recruit.getKey(), userId);
        }
    }

    @Override
    public void onMessageReactionRemove(@NotNull MessageReactionRemoveEvent event) {
        if (!event.isFromGuild() || !event.getGuild().getId().equals(guildId)) return;
        if (!event.getEmoji().equals(CHECK)) return;
        String userId = event.getUserId();
        if (userId.equals(event.getJDA().getSelfUser().getId())) return;

        Recruit recruit = findByMessage(event.getMessageId());
        if (recruit == null) return;
        recruit.removeParticipant(userId);
        logger.info("Reaction removed from {} by {}", recruit.getKey(), userId);
    }
}
