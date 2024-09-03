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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecruitManager extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(RecruitManager.class);
    private final Guild guild;
    private final Map<String, List<Recruit>> recruits = new HashMap<>();
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
            logger.debug("Recruit channel not found. Clearing recruit channel from database.");
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
            List<Recruit> recruitList = recruits.get(registerer.getId());
            recruitList.add(recruit);
        } else {
            // create new recruit
            recruits.put(registerer.getId(), new ArrayList<>(List.of(recruit)));
        }

        recruit.publish(recruitChannel, registerer);

        // remove expired recruits
        for (List<Recruit> recruitList : recruits.values()) {
            for (Recruit r : recruitList) {
                if (r.isDead) {
                    recruitList.remove(r);
                    logger.debug("Recruit removed");
                }
            }
        }
    }

    public void unregisterRecruit(Member registerer) {
        if (!recruits.containsKey(registerer.getId())) {
            return;
        }
        for (Recruit recruit : recruits.get(registerer.getId())) {
            recruit.destroy();
        }
        recruits.remove(registerer.getId());
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        User user = event.getUser();
        if (user == null || user.isBot()) return;

        logger.debug("Reaction added");
        // loop through all recruits
        for (List<Recruit> recruitList : recruits.values()) {
            for (Recruit recruit : recruitList) {
                if (recruit.getMessageId().equals(event.getMessageId())) {
                    if (event.getEmoji().equals(Emoji.fromUnicode("✅"))) {
                        boolean accepted = recruit.addParticipant(event.getMember());
                        if (!accepted) {
                            // remove reaction
                            event.getReaction().removeReaction(user).queue();
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onMessageReactionRemove(@NotNull MessageReactionRemoveEvent event) {
        User user = event.getUser();
        if (user == null || user.isBot()) return;

        logger.debug("Reaction removed");
        // loop through all recruits
        for (List<Recruit> recruitList : recruits.values()) {
            for (Recruit recruit : recruitList) {
                if (recruit.getMessageId().equals(event.getMessageId())) {
                    if (event.getEmoji().equals(Emoji.fromUnicode("✅"))) {
                        recruit.removeParticipant(event.getMember());
                    }
                }
            }
        }
    }
}
