package core.command;

import Utilities.TextStyler;
import Utilities.Util;
import core.Service;
import core.Version;
import exceptions.InvalidLolStartTimeException;
import exceptions.PermissionInsufficientException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.discord.MessageEmbedProps;
import service.leagueoflegends.Core.LolBox;
import service.music.Core.MusicBox;
import service.music.Core.MusicStreamer;
import service.watcher.AccessSession;
import service.watcher.GuildWatcher;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SlashCommandParser {
    private final Logger logger = LoggerFactory.getLogger(SlashCommandParser.class);

    public SlashCommandParser() {

    }

    public void test(SlashCommandInteractionEvent e) {
        e.reply(String.format("Test response: CushionBot v%s", Version.CURRENT)).queue();
    }

    public void finishMaintenance(SlashCommandInteractionEvent e) {
        try {
            Service.finishMaintenance(e);
            this.sendVolatileReply(e, "점검 완료 처리되었습니다.", 5);
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    public void serverRanking(SlashCommandInteractionEvent e) {
        try {
            TextChannel textChannel = e.getChannel().asTextChannel();
            Guild guild = e.getGuild();
            if (guild == null) {
                this.sendReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.");
                return;
            }

            Map<String, Map<String, List<AccessSession>>> accessSessions = GuildWatcher.accessSessions;
            Map<String, List<AccessSession>> guildSessions = accessSessions.get(guild.getId());
            List<Pair<String, Long>> guildRanking = new ArrayList<>();
            if (guildSessions == null || guildSessions.isEmpty()) {
                this.sendReply(e, "서버 랭킹 정보가 없습니다.");
                return;
            }

            for (Map.Entry<String, List<AccessSession>> entry : guildSessions.entrySet()) {
                String userId = entry.getKey();
                List<AccessSession> sessions = entry.getValue();
                long totalDuration = 0;
                for (AccessSession session : sessions) {
                    totalDuration += session.getDuration();
                }
                guildRanking.add(Pair.of(userId, totalDuration));
            }

            guildRanking.sort((a, b) -> (int) (b.getRight() - a.getRight()));
            guildRanking = guildRanking.subList(0, Math.min(5, guildRanking.size()));

            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("서버 랭킹");
            embedBuilder.setDescription("최근 1년간 서버 내 음성채널에 연결한 총 시간을 기준으로 정렬됩니다.");
            embedBuilder.setColor(new Color(104, 53, 255));

            for (int i = 0; i < guildRanking.size(); i++) {
                Pair<String, Long> pair = guildRanking.get(i);
                String userId = pair.getLeft();
                long totalDuration = pair.getRight();
                String durationStr = Util.getDurationString(totalDuration);
                embedBuilder.addField(String.format("[%d위]", i + 1), String.format("<@%s> %s", userId, TextStyler.Block(durationStr)), false);
            }

            MessageEmbed embed = embedBuilder.build();
            e.replyEmbeds(embed).queue();
        } catch (Exception err) {
            err.printStackTrace();
            e.reply(String.format("오류: %s", err.getMessage())).queue();
        }
    }

    public void myRanking(SlashCommandInteractionEvent e) {
        try {
            TextChannel textChannel = e.getChannel().asTextChannel();
            Guild guild = e.getGuild();
            if (guild == null) {
                this.sendReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.");
                return;
            }

            Map<String, Map<String, List<AccessSession>>> accessSessions = GuildWatcher.accessSessions;
            Map<String, List<AccessSession>> guildSessions = accessSessions.get(guild.getId());
            List<Pair<String, Long>> guildRanking = new ArrayList<>();
            if (guildSessions == null || guildSessions.isEmpty()) {
                this.sendReply(e, "서버 랭킹 정보가 없습니다.");
                return;
            }

            for (Map.Entry<String, List<AccessSession>> entry : guildSessions.entrySet()) {
                String userId = entry.getKey();
                List<AccessSession> sessions = entry.getValue();
                long totalDuration = 0;
                for (AccessSession session : sessions) {
                    totalDuration += session.getDuration();
                }
                guildRanking.add(Pair.of(userId, totalDuration));
            }

            guildRanking.sort((a, b) -> (int) (b.getRight() - a.getRight()));
            Integer ranking = null;
            long totalDuration = 0;
            for (int i = 0; i < guildRanking.size(); i++) {
                Pair<String, Long> pair = guildRanking.get(i);
                String userId = pair.getLeft();
                if (userId.equals(e.getUser().getId())) {
                    ranking = i + 1;
                    totalDuration = pair.getRight();
                    break;
                }
            }

            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("개인 랭킹");
            embedBuilder.setDescription("최근 1년간 서버 내 음성채널에 연결한 총 시간을 기준으로 랭크가 매겨집니다.");
            embedBuilder.setColor(new Color(104, 53, 255));
            embedBuilder.setThumbnail(e.getUser().getAvatarUrl());
            embedBuilder.addField("순위", TextStyler.Block(ranking != null ? String.format("%d위", ranking) : "순위권 외"), false);
            embedBuilder.addField("총 연결 시간", TextStyler.Block(Util.getDurationString(totalDuration)), false);

            MessageEmbed embed = embedBuilder.build();
            e.replyEmbeds(embed).queue();
        } catch (Exception err) {
            err.printStackTrace();
            e.reply(String.format("오류: %s", err.getMessage())).queue();
        }
    }

    public void clear(SlashCommandInteractionEvent e) {
        TextChannel textChannel = e.getChannel().asTextChannel();
        try {
            OptionMapping amountOpt = e.getOption("message_count");
            int amount = amountOpt == null ? 1 : Math.min(amountOpt.getAsInt(), 500);
            Member author = e.getMember();
            if (author == null) {
                this.sendVolatileReply(e, "메시지 삭제 권한이 없습니다.", 5);
                return;
            }

            MessageHistory messageHistory = textChannel.getHistory();
            messageHistory.retrievePast(amount).queue(messageList -> {
                try {
                    textChannel.purgeMessages(messageList);
                    String boldStr = TextStyler.Bold("최근 메시지 " + amount + "개가 " + TextStyler.member(author) + "에 의해 삭제 요청되었습니다.");
                    e.reply(boldStr).queue();
//                    textChannel.deleteMessages(messageList).queue(
//                            success -> {
//                                if (author == null) return;
//                                String boldStr = TextStyler.Bold("최근 메시지 " + amount + "개가 " + TextStyler.Block(author.getEffectiveName()) + "에 의해 삭제되었습니다.");
//                                e.reply(boldStr).queue();
//                            },
//                            failure -> {
//                                if (author == null) return;
//                                logger.error("Failed to delete messages by {}", author.getEffectiveName());
//                            }
//                    );
                } catch (Exception err) {
                    if (err.getMessage().contains("older than")) {
                        e.reply("14일 이상된 메시지는 삭제할 수 없습니다.").queue();
                        return;
                    }
                    err.printStackTrace();
                    e.reply("메시지 삭제 중 오류가 발생했습니다.").queue();
                }
            });
        } catch (NumberFormatException exception) {
            textChannel.sendMessage("잘못된 인자: clear 명령이 취소되었습니다.").queue();
        }
    }

    public void music(SlashCommandInteractionEvent e) {
        try {
            Guild guild = e.getGuild();
            if (guild == null) {
                this.sendVolatileReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.", 5);
                return;
            }
            String guildId = guild.getId();
            TextChannel textChannel = e.getChannel().asTextChannel();
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

    public void musicShuffle(SlashCommandInteractionEvent e) {
        try {
            Guild guild = e.getGuild();
            if (guild == null) {
                this.sendVolatileReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.", 5);
                return;
            }
            String guildId = guild.getId();
            Service.addGuildManagerIfNotExists(guild);
            MusicBox musicBox = Service.GetMusicBoxByGuildId(guildId);
            MusicStreamer musicStreamer = musicBox.getStreamer();
            musicStreamer.shuffleTracksOnQueue();
            musicBox.updateEmbed();
            this.sendVolatileReply(e, "음악이 셔플되었습니다.", 5);
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    public void musicVolume(SlashCommandInteractionEvent e) {
        try {
            Guild guild = e.getGuild();
            if (guild == null) {
                this.sendVolatileReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.", 5);
                return;
            }
            String guildId = guild.getId();
            Service.addGuildManagerIfNotExists(guild);
            MusicBox musicBox = Service.GetMusicBoxByGuildId(guildId);
            MusicStreamer musicStreamer = musicBox.getStreamer();
            OptionMapping volumeOpt = e.getOption("볼륨");
            if (volumeOpt == null) {
                this.sendVolatileReply(e, "볼륨을 입력해주세요.", 5);
                return;
            }
            int volume = volumeOpt.getAsInt();
            musicStreamer.setVolume(volume);
            musicBox.updateEmbed();
            this.sendVolatileReply(e, String.format("볼륨이 %d%%로 설정되었습니다. 몇 초내에 적용됩니다.", volume), 5);
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    public void lol5vs5(SlashCommandInteractionEvent e) {
        try {
            Guild guild = e.getGuild();
            if (guild == null) {
                this.sendVolatileReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.", 5);
                return;
            }
            String guildId = guild.getId();
            TextChannel textChannel = e.getChannel().asTextChannel();
            Service.addGuildManagerIfNotExists(guild);
            LolBox lolBox = Service.GetLolBoxByGuildId(guildId);
            lolBox.setLolChannel(textChannel);

            MessageEmbedProps embedPair = lolBox.getEmbed();
            ReplyCallbackAction action = e.replyEmbeds(embedPair.messageEmbed);
            for (ActionRow actionRow : embedPair.actionRows) {
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
            if (guild == null) {
                this.sendVolatileReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.", 5);
                return;
            }
            String guildId = guild.getId();
            Service.addGuildManagerIfNotExists(guild);
            LolBox lolBox = Service.GetLolBoxByGuildId(guildId);
            TextChannel textChannel = lolBox.getLolChannel();
            if (textChannel == null) {
                this.sendVolatileReply(e, "내전 채널이 설정되지 않았습니다. /내전채널 명령어로 설정해주세요.", 8);
                return;
            }

            if (!this.isManager(e.getMember())) throw new PermissionInsufficientException();
            if (start) {
                OptionMapping timeOpt = e.getOption("시간");
                OptionMapping minuteOpt = e.getOption("분");
                if (timeOpt == null || minuteOpt == null) {
                    throw new InvalidLolStartTimeException();
                }

                int time = timeOpt.getAsInt();
                int minute = minuteOpt.getAsInt();

                lolBox.startCollectingTeam(time, minute);
                this.sendVolatileReply(e, "내전 인원 모집이 시작되었습니다. 참여 여부를 선택해주세요!", 8);
            } else {
                lolBox.stopCollectingTeam();
                this.sendVolatileReply(e, "내전이 종료 또는 취소되었습니다.", 8);
            }
            lolBox.updateEmbed();
        } catch (InvalidLolStartTimeException exception) {
            String msg = "내전 시간이 유효하지 않습니다." + (exception.getMessage() == null ? "" : " " + exception.getMessage());
            e.reply(msg).queue(interactionHook -> {
                interactionHook.retrieveOriginal().queue(message -> {
                    message.delete().queueAfter(8, java.util.concurrent.TimeUnit.SECONDS);
                });
            });
        } catch (PermissionInsufficientException exception) {
            this.sendVolatileReply(e, "내전을 등록/취소할 권한이 없습니다.", 8);
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    public void lol5vs5ChangeStartTime(SlashCommandInteractionEvent e) {
        try {
            Guild guild = e.getGuild();
            if (guild == null) {
                this.sendVolatileReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.", 5);
                return;
            }
            String guildId = guild.getId();
            Service.addGuildManagerIfNotExists(guild);
            LolBox lolBox = Service.GetLolBoxByGuildId(guildId);
            TextChannel textChannel = lolBox.getLolChannel();
            if (textChannel == null) {
                this.sendVolatileReply(e, "내전 채널이 설정되지 않았습니다. /내전채널 명령어로 설정해주세요.", 8);
                return;
            }

            if (!this.isManager(e.getMember())) throw new PermissionInsufficientException();
            if (!lolBox.isCollectingTeam()) {
                this.sendVolatileReply(e, "현재 내전 일정이 없습니다. 생성하려면 /내전모으기 명령어로 생성해주세요.", 8);
                return;
            }

            OptionMapping timeOpt = e.getOption("시간");
            OptionMapping minuteOpt = e.getOption("분");
            if (timeOpt == null || minuteOpt == null) {
                throw new InvalidLolStartTimeException();
            }

            int time = timeOpt.getAsInt();
            int minute = minuteOpt.getAsInt();

            long originalTime = lolBox.getStartTime();
            String originalTimeStr = Util.timeFormat(originalTime, "a hh시 mm분");
            long newTime = lolBox.calculateStartTime(time, minute);
            String newTimeStr = Util.timeFormat(newTime, "a hh시 mm분");

            lolBox.setStartTime(time, minute);
            this.sendVolatileReply(e, String.format("내전 시작시간이 %s에서 %s으로 변경되었습니다.", originalTimeStr, newTimeStr), 8);
            lolBox.updateEmbed();
        } catch (InvalidLolStartTimeException exception) {
            String msg = "내전 시간이 유효하지 않습니다." + (exception.getMessage() == null ? "" : " " + exception.getMessage());
            e.reply(msg).queue(interactionHook -> {
                interactionHook.retrieveOriginal().queue(message -> {
                    message.delete().queueAfter(8, java.util.concurrent.TimeUnit.SECONDS);
                });
            });
        } catch (PermissionInsufficientException exception) {
            this.sendVolatileReply(e, "내전 시간을 변경할 권한이 없습니다.", 8);
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    public void lol5vs5PrintInfo(SlashCommandInteractionEvent e) {
        try {
            Guild guild = e.getGuild();
            if (guild == null) {
                this.sendVolatileReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.", 5);
                return;
            }
            String guildId = guild.getId();
            Service.addGuildManagerIfNotExists(guild);
            LolBox lolBox = Service.GetLolBoxByGuildId(guildId);
            TextChannel textChannel = lolBox.getLolChannel();
            if (textChannel == null) {
                this.sendVolatileReply(e, "내전 채널이 설정되지 않았습니다. /내전채널 명령어로 설정해주세요.", 8);
                return;
            }

            StringBuilder sb = new StringBuilder();
            if (lolBox.isCollectingTeam()) {
                long startTime = lolBox.getStartTime();
                String remainTime = Util.getRelativeTime(startTime);
                String absoluteTime = Util.timeFormat(startTime, "a hh시 mm분");

                if (startTime > System.currentTimeMillis()) {
                    sb.append(String.format("내전이 %s 뒤에 시작합니다. (%s)", remainTime, absoluteTime));
                } else {
                    sb.append("내전이 곧 시작됩니다.");
                }
                sb.append("\n현재 참여투표한 사람 수: ");
                sb.append(TextStyler.Block(lolBox.getJoiners().size() + ""));
                sb.append("\n");
                sb.append(String.format("%s 채널에서 내전 참여/불참에 투표하세요.", textChannel.getAsMention()));
            } else {
                sb.append("현재 아직 내전 일정이 없습니다.");
            }
            e.reply(sb.toString()).queue();
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    public void lol5vs5Call(SlashCommandInteractionEvent e) {
        try {
            Guild guild = e.getGuild();
            if (guild == null) {
                this.sendVolatileReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.", 5);
                return;
            }
            String guildId = guild.getId();
            Service.addGuildManagerIfNotExists(guild);
            LolBox lolBox = Service.GetLolBoxByGuildId(guildId);
            TextChannel textChannel = lolBox.getLolChannel();
            if (textChannel == null) {
                this.sendVolatileReply(e, "내전 채널이 설정되지 않았습니다. /내전채널 명령어로 설정해주세요.", 8);
                return;
            }

            if (!this.isManager(e.getMember())) throw new PermissionInsufficientException();

            if (!lolBox.isCollectingTeam()) {
                this.sendVolatileReply(e, "현재 내전 일정이 없습니다. 생성하려면 /내전모으기 명령어로 생성해주세요.", 8);
                return;
            }

            long startTime = lolBox.getStartTime();
            ArrayList<Member> joiners = lolBox.getJoiners();
            String remainTime = Util.getRelativeTime(startTime);
            StringBuilder sb = new StringBuilder();

            if (startTime > System.currentTimeMillis()) {
                sb.append(String.format("내전이 %s 뒤에 시작합니다. 태그되는 참여자 분들은 준비해주세요.\n", remainTime));
            } else {
                sb.append("내전 일정에 등록된 시간이 되었습니다. 태그되는 참여자 분들은 준비해주세요.\n");
            }
            for (Member m : joiners) {
                sb.append(m.getAsMention());
                sb.append(" ");
            }
            e.reply(sb.toString()).queue();
        } catch (PermissionInsufficientException exception) {
            this.sendVolatileReply(e, "내전 시간을 변경할 권한이 없습니다.", 8);
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    private void sendReply(SlashCommandInteractionEvent e, String message) {
        e.reply(message).queue();
    }

    private void sendVolatileReply(SlashCommandInteractionEvent e, String message, int delay) {
        e.reply(message).queue(interactionHook -> {
            interactionHook.retrieveOriginal().queue(message1 -> {
                message1.delete().queueAfter(delay, java.util.concurrent.TimeUnit.SECONDS);
            });
        });
    }

    private boolean isManager(Member member) {
        return member.isOwner() || member.hasPermission(Permission.ADMINISTRATOR);
    }
}
