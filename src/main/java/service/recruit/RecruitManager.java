package service.recruit;

import core.CushionBot;
import exceptions.RecruitPublishException;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
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

import java.util.HashMap;
import java.util.Map;

public class RecruitManager extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(RecruitManager.class);
    private final Guild guild;
    private final Map<String, Map<String, Recruit>> recruits = new HashMap<>();
    public TextChannel recruitChannel;

    public RecruitManager(Guild guild) {
        this.guild = guild;

        // get recruit channel from database
        String recruitChannelKey = GuildUtil.recruitChannelKey(guild.getId());
        if (RedisClient.has(recruitChannelKey)) {
            String channelId = RedisClient.get(recruitChannelKey);
            TextChannel channel = guild.getTextChannelById(channelId);
            if (channel != null) {
                recruitChannel = channel;
            }
        }

        if (recruitChannel != null && CushionBot.jda.getTextChannelById(recruitChannel.getId()) == null) {
            recruitChannel = null;
            logger.info("Recruit channel not found. Clearing recruit channel from database.");
            RedisClient.del(recruitChannelKey);
        }

        if (recruitChannel != null) {
            // purge all recruit messages
            recruitChannel.getIterableHistory().forEach(message -> {
                message.delete().queue();
            });
        }

        CushionBot.jda.addEventListener(this);
    }

    public void registerRecruit(Member registerer, Recruit recruit) throws RecruitPublishException {
        if (recruits.containsKey(registerer.getId())) {
            // update recruit
            Map<String, Recruit> recruitMap = recruits.get(registerer.getId());
            recruitMap.put(recruit.getKey(), recruit);
        } else {
            // create new recruit
            Map<String, Recruit> newRecruitMap = new HashMap<>();
            newRecruitMap.put(recruit.getKey(), recruit);
            recruits.put(registerer.getId(), newRecruitMap);
        }

        recruit.publish(recruitChannel, registerer);

        // remove expired recruits
        for (Map<String, Recruit> recruitMap : recruits.values()) {
            for (Recruit r : recruitMap.values()) {
                if (r.isDead) {
                    recruitMap.remove(r.getKey());
                    logger.info("Recruit removed");
                }
            }
        }
    }

    public boolean unregisterRecruit(Member registerer, String key) {
        if (!recruits.containsKey(registerer.getId())) {
            return false;
        }

        Map<String, Recruit> userRecruits = recruits.get(registerer.getId());
        if (key == null) {
            for (Recruit recruit : userRecruits.values()) {
                recruit.destroy();
            }
            recruits.remove(registerer.getId());
        } else {
            Recruit recruit = userRecruits.get(key);
            if (recruit != null) {
                recruit.destroy();
                userRecruits.remove(key);
            } else {
                logger.warn("Recruit not found for key: {}", key);
                return false;
            }

            if (userRecruits.isEmpty()) {
                recruits.remove(registerer.getId());
            }
        }
        return true;
    }

    public boolean changeRecruitTime(Member registerer, String key, long newRecruitAt) {
        if (!recruits.containsKey(registerer.getId())) {
            return false;
        }

        Map<String, Recruit> userRecruits = recruits.get(registerer.getId());
        Recruit recruit = userRecruits.get(key);
        if (recruit != null) {
            recruit.setRecruitAt(newRecruitAt);
            return true;
        } else {
            logger.warn("Recruit not found for key: {}", key);
            return false;
        }
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        User user = event.getUser();
        if (user == null || user.isBot()) return;
        Guild guild = event.getGuild();
        if (guild != this.guild) return;

        // loop through all recruits
        for (Map<String, Recruit> recruitMap : recruits.values()) {
            for (Recruit recruit : recruitMap.values()) {
                if (recruit.getMessageId().equals(event.getMessageId())) {
                    if (event.getEmoji().equals(Emoji.fromUnicode("✅"))) {
                        boolean accepted = recruit.addParticipant(event.getMember());
                        if (!accepted) {
                            // remove reaction
                            event.getReaction().removeReaction(user).queue();
                            logger.info("Reaction add blocked for {} by {}", recruit.getKey(), user.getEffectiveName());
                        } else {
                            logger.info("Reaction added to {} by {}", recruit.getKey(), user.getEffectiveName());
                        }
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void onMessageReactionRemove(@NotNull MessageReactionRemoveEvent event) {
        User user = event.getUser();
        if (user == null || user.isBot()) return;
        Guild guild = event.getGuild();
        if (guild != this.guild) return;

        // loop through all recruits
        for (Map<String, Recruit> recruitMap : recruits.values()) {
            for (Recruit recruit : recruitMap.values()) {
                if (recruit.getMessageId().equals(event.getMessageId())) {
                    if (event.getEmoji().equals(Emoji.fromUnicode("✅"))) {
                        recruit.removeParticipant(event.getMember());
                        logger.info("Reaction removed from {} by {}", recruit.getKey(), user.getEffectiveName());
                        return;
                    }
                }
            }
        }
    }
}
