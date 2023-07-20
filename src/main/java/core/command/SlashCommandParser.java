package core.command;

import core.InternalEventListener;
import core.Service;
import core.Version;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.discord.MessageEmbedPair;
import service.leagueoflegends.Core.LolBox;
import service.music.Core.MusicActionEmbed;
import service.music.Core.MusicBox;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class SlashCommandParser {
    private final Logger logger = LoggerFactory.getLogger(SlashCommandParser.class);

    public SlashCommandParser() {

    }

    public void test(SlashCommandInteractionEvent e) {
        e.reply(String.format("Test response: CushionBot v%s", Version.CURRENT)).queue();
    }

    public void music(SlashCommandInteractionEvent e) {
        try {
            Guild guild = e.getGuild();
            if(guild == null) {
                e.reply("This command can only be used in a guild.").queue();
                return;
            }
            String guildId = guild.getId();
            TextChannel textChannel = e.getTextChannel();
            Service.addGuildManagerIfNotExists(guild);
            MusicBox musicBox = Service.GetMusicBoxByGuildId(guildId);
            musicBox.setMusicChannel(textChannel);

            MessageEmbed embed = musicBox.getInitialSettingEmbed();
            e.replyEmbeds(embed).queue(interactionHook -> {
                interactionHook.retrieveOriginal().queue(musicBox::setMusicBoxMessage);
            });
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    public void lol5vs5(SlashCommandInteractionEvent e) {
        try {
            Guild guild = e.getGuild();
            if(guild == null) {
                e.reply("This command can only be used in a guild.").queue();
                return;
            }
            String guildId = guild.getId();
            TextChannel textChannel = e.getTextChannel();
            Service.addGuildManagerIfNotExists(guild);
            LolBox lolBox = Service.GetLolBoxByGuildId(guildId);
            lolBox.setLolChannel(textChannel);

            MessageEmbedPair embedPair = lolBox.getEmbed();
            ReplyCallbackAction action = e.replyEmbeds(embedPair.messageEmbed);
            for(ActionRow actionRow : embedPair.actionRows) {
                action = action.addActionRow((ItemComponent) actionRow);
            }
            action.queue(interactionHook -> {
                interactionHook.retrieveOriginal().queue(lolBox::setLolBoxMessage);
            });
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    public void lol5vs5StartOrStop(SlashCommandInteractionEvent e, boolean start) {
        try {
            Guild guild = e.getGuild();
            if(guild == null) {
                e.reply("This command can only be used in a guild.").queue();
                return;
            }
            String guildId = guild.getId();
            Service.addGuildManagerIfNotExists(guild);
            LolBox lolBox = Service.GetLolBoxByGuildId(guildId);
            TextChannel textChannel = lolBox.getLolChannel();
            if(textChannel == null) {
                e.reply("내전 채널이 설정되지 않았습니다. /내전 명령어로 설정해주세요.").queue(interactionHook -> {
                    interactionHook.retrieveOriginal().queue(message -> {
                        message.delete().queueAfter(8, java.util.concurrent.TimeUnit.SECONDS);
                    });
                });
                return;
            }

            Member sender = e.getMember();
            Member manager = guild.getOwner();
            if(sender.getId().equals(manager.getId())) {
                if(start) {
                    lolBox.startCollectingTeam();
                    e.reply("내전 일정이 등록되었습니다. 이제부터 내전에 참여 여부를 선택할 수 있습니다!").queue(interactionHook -> {
                        interactionHook.retrieveOriginal().queue(message -> {
                            message.delete().queueAfter(8, java.util.concurrent.TimeUnit.SECONDS);
                        });
                    });
                } else {
                    lolBox.stopCollectingTeam();
                    e.reply("내전이 취소되었습니다.").queue(interactionHook -> {
                        interactionHook.retrieveOriginal().queue(message -> {
                            message.delete().queueAfter(8, java.util.concurrent.TimeUnit.SECONDS);
                        });
                    });
                }
                lolBox.updateEmbed();
            } else {
                e.reply("내전을 등록/취소할 권한이 없습니다.").queue(interactionHook -> {
                    interactionHook.retrieveOriginal().queue(message -> {
                        message.delete().queueAfter(8, java.util.concurrent.TimeUnit.SECONDS);
                    });
                });
            }
        } catch (Exception err) {
            err.printStackTrace();
        }
    }
}
