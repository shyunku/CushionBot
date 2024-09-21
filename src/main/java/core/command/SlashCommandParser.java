package core.command;

import Utilities.Request;
import Utilities.TextStyler;
import Utilities.TimeUtil;
import Utilities.Util;
import core.Service;
import core.Version;
import dtos.teamgg.SetSummonerLineFavorRequestDto;
import exceptions.InvalidLolStartTimeException;
import exceptions.PermissionInsufficientException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.discord.MessageEmbedProps;
import service.guild.core.GuildUtil;
import service.inmemory.RedisClient;
import service.leagueoflegends.Core.LolBox;
import service.music.Core.MusicBox;
import service.music.Core.MusicStreamer;
import service.recruit.Recruit;
import service.recruit.RecruitManager;
import service.watcher.AccessSession;
import service.watcher.GuildWatcher;

import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.*;

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

    public void recruitChannel(SlashCommandInteractionEvent e) {
        try {
            Guild guild = e.getGuild();
            if (guild == null) {
                this.sendVolatileReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.", 5);
                return;
            }
            String guildId = guild.getId();
            Service.addGuildManagerIfNotExists(guild);
            RecruitManager recruitManager = Service.GetRecruitManagerByGuildId(guildId);
            String recruitChannelKey = GuildUtil.recruitChannelKey(guildId);

            if (!this.isManager(e.getMember())) throw new PermissionInsufficientException();

            OptionMapping channelOpt = e.getOption("채널");
            if (channelOpt == null) {
                // create new channel
                guild.createTextChannel("구인채널").queue(textChannel -> {
                    RedisClient.set(recruitChannelKey, textChannel.getId());
                    recruitManager.recruitChannel = textChannel;
                    this.setRecruitChannel(textChannel);
                    e.reply(String.format("구인 채널이 지정되었습니다. 이제 %s 채널에서 구인 정보를 확인하실 수 있습니다.", textChannel.getAsMention())).queue();
                });
            } else {
                TextChannel textChannel = channelOpt.getAsChannel().asTextChannel();

                // save channel
                RedisClient.set(recruitChannelKey, textChannel.getId());
                recruitManager.recruitChannel = textChannel;
                this.setRecruitChannel(textChannel);
                e.reply(String.format("구인 채널이 지정되었습니다. 이제 %s 채널에서 구인 정보를 확인하실 수 있습니다.", textChannel.getAsMention())).queue();
            }
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    public void recruit(SlashCommandInteractionEvent e) {
        try {
            Guild guild = e.getGuild();
            if (guild == null) {
                this.sendVolatileReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.", 5);
                return;
            }
            String guildId = guild.getId();
            Service.addGuildManagerIfNotExists(guild);
            RecruitManager recruitManager = Service.GetRecruitManagerByGuildId(guildId);
            if (recruitManager == null || recruitManager.recruitChannel == null) {
                this.sendVolatileReply(e, "구인 채널이 설정되지 않았습니다. /구인채널생성 명령어로 설정해주세요.", 8);
                return;
            }

            Member member = e.getMember();
            if (member == null) {
                this.sendVolatileReply(e, "구인 정보를 등록할 수 없습니다.", 5);
                return;
            }

            TextInput gameNameInput = TextInput.create("gameName", "게임 이름/이벤트 이름을 입력해주세요.", TextInputStyle.SHORT)
                    .setPlaceholder("게임 이름/이벤트 이름 (ex. 자랭)")
                    .setRequired(true)
                    .build();
            TextInput recruitingNumInput = TextInput.create("recruitNum", "모집 인원을 숫자로 입력해주세요. (옵션)", TextInputStyle.SHORT)
                    .setPlaceholder("모집 인원 (ex. 5)")
                    .setRequired(false)
                    .build();
            TextInput timeInput = TextInput.create("time", "모집 시간을 입력해주세요. 현재 시각보다 전일 경우 내일로 설정됩니다. (옵션)", TextInputStyle.SHORT)
                    .setPlaceholder("모집 시간 (ex. 21:30)")
                    .setRequired(false)
                    .build();
            TextInput durationInput = TextInput.create("duration", "예상 소요 시간을 입력해주세요. (옵션, 1시간 단위)", TextInputStyle.SHORT)
                    .setPlaceholder("소요 시간 (ex. 1시간 반 -> 1.5)")
                    .setRequired(false)
                    .build();

            Modal modal = Modal.create("recruitModal", "구인 등록")
                    .addActionRow(gameNameInput)
                    .addActionRow(recruitingNumInput)
                    .addActionRow(timeInput)
                    .addActionRow(durationInput)
                    .build();

            e.replyModal(modal).queue();
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    public void recruitCancel(SlashCommandInteractionEvent e) {
        try {
            Guild guild = e.getGuild();
            if (guild == null) {
                this.sendVolatileReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.", 5);
                return;
            }
            String guildId = guild.getId();
            Service.addGuildManagerIfNotExists(guild);
            RecruitManager recruitManager = Service.GetRecruitManagerByGuildId(guildId);
            if (recruitManager == null) {
                this.sendVolatileReply(e, "구인 채널이 설정되지 않았습니다. /구인채널 명령어로 설정해주세요.", 8);
                return;
            }

            Member member = e.getMember();
            if (member == null) {
                this.sendVolatileReply(e, "구인 정보를 삭제할 수 없습니다.", 5);
                return;
            }

            OptionMapping recruitKeyInput = e.getOption("구인코드");
            if (recruitKeyInput == null || recruitKeyInput.getAsString().isEmpty()) {
                recruitManager.unregisterRecruit(member, null);
            } else {
                String recruitKey = recruitKeyInput.getAsString();
                boolean success = recruitManager.unregisterRecruit(member, recruitKey);
                if (!success) {
                    this.sendVolatileReply(e, "구인 정보를 찾을 수 없습니다.", 5);
                    return;
                }
            }

            this.sendVolatileReply(e, "구인이 취소되었습니다.", 5);
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    public void recruitModify(SlashCommandInteractionEvent e) {
        try {
            Guild guild = e.getGuild();
            if (guild == null) {
                this.sendVolatileReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.", 5);
                return;
            }
            String guildId = guild.getId();
            Service.addGuildManagerIfNotExists(guild);
            RecruitManager recruitManager = Service.GetRecruitManagerByGuildId(guildId);
            if (recruitManager == null) {
                this.sendVolatileReply(e, "구인 채널이 설정되지 않았습니다. /구인채널 명령어로 설정해주세요.", 8);
                return;
            }

            Member member = e.getMember();
            if (member == null) {
                this.sendVolatileReply(e, "구인 정보를 변경할 수 없습니다.", 5);
                return;
            }

            OptionMapping recruitKeyInput = e.getOption("구인코드");
            if (recruitKeyInput == null || recruitKeyInput.getAsString().isEmpty()) {
                this.sendVolatileReply(e, "구인 코드를 입력해주세요.", 5);
                return;
            }

            OptionMapping timeInput = e.getOption("시간");
            OptionMapping recruitNumInput = e.getOption("인원");
            boolean timeGiven = timeInput != null && !timeInput.getAsString().isEmpty();
            boolean recruitNumGiven = recruitNumInput != null && recruitNumInput.getAsInt() > 0;

            if (!timeGiven && !recruitNumGiven) {
                this.sendVolatileReply(e, "변경할 내용을 입력해주세요.", 5);
                return;
            }

            if (timeGiven) {
                DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                int hour, minute;
                try {
                    LocalTime time = LocalTime.parse(timeInput.getAsString(), timeFormatter);
                    hour = time.getHour();
                    minute = time.getMinute();
                } catch (DateTimeParseException e2) {
                    throw new IllegalArgumentException("시간 형식이 올바르지 않습니다: (e.g., 21:30).");
                }

                Calendar recruitAt = TimeUtil.getKstCalendar();
                recruitAt.set(Calendar.HOUR_OF_DAY, hour);
                recruitAt.set(Calendar.MINUTE, minute);
                recruitAt.set(Calendar.SECOND, 0);

                String recruitKey = recruitKeyInput.getAsString();
                boolean success = recruitManager.changeRecruitTime(member, recruitKey, recruitAt.getTimeInMillis());
                if (!success) {
                    this.sendVolatileReply(e, "구인 정보를 찾을 수 없습니다.", 5);
                    return;
                }
            }

            if (recruitNumGiven) {
                int recruitNum = recruitNumInput.getAsInt();
                String recruitKey = recruitKeyInput.getAsString();
                boolean success = recruitManager.changeRecruitNum(member, recruitKey, recruitNum);
                if (!success) {
                    this.sendVolatileReply(e, "구인 정보를 찾을 수 없습니다.", 5);
                    return;
                }
            }

            this.sendVolatileReply(e, "구인 정보가 변경되었습니다.", 5);
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    public void recruitAd(SlashCommandInteractionEvent e) {
        try {
            Guild guild = e.getGuild();
            if (guild == null) {
                this.sendVolatileReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.", 5);
                return;
            }
            String guildId = guild.getId();
            Service.addGuildManagerIfNotExists(guild);
            RecruitManager recruitManager = Service.GetRecruitManagerByGuildId(guildId);
            if (recruitManager == null) {
                this.sendVolatileReply(e, "구인 채널이 설정되지 않았습니다. /구인채널 명령어로 설정해주세요.", 8);
                return;
            }

            Member member = e.getMember();
            if (member == null) {
                this.sendVolatileReply(e, "멤버가 아닙니다.", 5);
                return;
            }

            OptionMapping recruitKeyInput = e.getOption("구인코드");
            if (recruitKeyInput == null || recruitKeyInput.getAsString().isEmpty()) {
                this.sendVolatileReply(e, "구인 코드를 입력해주세요.", 5);
                return;
            }

            String recruitKey = recruitKeyInput.getAsString();
            Recruit recruit = recruitManager.getRecruit(recruitKey);
            if (recruit == null) {
                this.sendVolatileReply(e, "구인 정보를 찾을 수 없습니다.", 5);
                return;
            }

            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("구인 광고");
            eb.setDescription(String.format("현재 %s를(을) 같이하실 분을 구하고 있습니다.", recruit.getGameName()));
            eb.setColor(0x3D99FF);
            eb.addField("구인코드", TextStyler.Block(recruitKey), true);
            eb.addField("참여하기", String.format("%s 채널에서 확인하세요.", recruitManager.recruitChannel.getAsMention()), false);
            e.reply("@everyone").setEmbeds(eb.build()).queue();
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    public void teamggRegister(SlashCommandInteractionEvent e) {
        try {
            Guild guild = e.getGuild();
            if (guild == null) {
                this.sendVolatileReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.", 5);
                return;
            }
            String guildId = guild.getId();
            Member member = e.getMember();
            if (member == null) {
                this.sendVolatileReply(e, "멤버를 식별할 수 없습니다.", 5);
                return;
            }

            String summonerName = "";
            String tag = "";

            OptionMapping summonerNameInput = e.getOption("소환사명");
            if (summonerNameInput == null || summonerNameInput.getAsString().isEmpty()) {
                this.sendVolatileReply(e, "소환사명을 입력해주세요.", 5);
                return;
            } else {
                summonerName = summonerNameInput.getAsString().trim();
            }

            OptionMapping tagInput = e.getOption("태그");
            if (tagInput == null || tagInput.getAsString().isEmpty()) {
                tag = "KR1";
            } else {
                tag = tagInput.getAsString().trim();
            }

            try {
                String puuid = Request.get(String.format("https://teamgg.kr:7713/v1/api/summonerPuuid?gameName=%s&tagLine=%s", summonerName, tag), String.class);
                RedisClient.set(GuildUtil.teamggSummonerKey(guildId, member.getId()), puuid);
                e.reply(String.format("%s님의 소환사가 \"%s#%s\"로 등록되었습니다.", member.getAsMention(), summonerName, tag)).queue();
            } catch (Exception err) {
                err.printStackTrace();
                e.reply("등록 실패: 서버 오류가 발생했습니다.").queue();
            }
        } catch (Exception err) {
            err.printStackTrace();
            e.reply("등록 실패: 오류가 발생했습니다.").queue();
        }
    }

    public void teamggLineFavor(SlashCommandInteractionEvent e) {
        try {
            Guild guild = e.getGuild();
            if (guild == null) {
                this.sendVolatileReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.", 5);
                return;
            }
            String guildId = guild.getId();
            Member member = e.getMember();
            if (member == null) {
                this.sendVolatileReply(e, "멤버를 식별할 수 없습니다.", 5);
                return;
            }

            String puuid = RedisClient.get(GuildUtil.teamggSummonerKey(guildId, member.getId()));
            if (puuid == null || puuid.isEmpty()) {
                this.sendVolatileReply(e, "소환사 정보가 등록되지 않았습니다. /팀지지등록으로 등록해주세요.", 5);
                return;
            }

            OptionMapping favorsInput = e.getOption("선호도");
            if (favorsInput == null || favorsInput.getAsString().isEmpty()) {
                this.sendVolatileReply(e, "선호도를 입력해주세요.", 5);
                return;
            }

            String favors = favorsInput.getAsString();
            ArrayList<String> favorList = new ArrayList<>(Arrays.asList(favors.split(",")));
            ArrayList<Integer> favorIntList = new ArrayList<>();
            for (String favor : favorList) {
                try {
                    favorIntList.add(Integer.parseInt(favor.trim()));
                } catch (NumberFormatException err) {
                    this.sendVolatileReply(e, "선호도는 숫자로 입력해주세요. (-1,0,1,2)", 5);
                    return;
                }
            }
            if (favorIntList.size() != 5) {
                this.sendVolatileReply(e, "선호도는 5개의 숫자로 쉼표(,)로 구분하여 입력해주세요. (탑, 정글, 미드, 원딜, 서폿 순)", 5);
                return;
            }

            int[] favorArray = new int[5];
            for (int i = 0; i < 5; i++) {
                favorArray[i] = favorIntList.get(i);
            }

            try {
                String customGameConfigId = "345c559e-a037-45b3-bac1-e7e272bc29e1";
                SetSummonerLineFavorRequestDto requestDto = new SetSummonerLineFavorRequestDto(customGameConfigId, puuid, favorArray);
                String response = Request.post("https://teamgg.kr:7713/v1/api/summonerLineFavor", requestDto, String.class);
                e.reply(String.format("%s님의 라인 선호도가 탑(%d) 정글(%d) 미드(%d) 원딜(%d) 서폿(%d)로 설정되었습니다.",
                        member.getAsMention(), favorArray[0], favorArray[1], favorArray[2], favorArray[3], favorArray[4]
                )).queue();
            } catch (Exception err) {
                err.printStackTrace();
                e.reply("선호도 등록 실패: 서버 오류가 발생했습니다.").queue();
            }
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    private void setRecruitChannel(TextChannel textChannel) {
        TextChannelManager textChannelManager = textChannel.getManager();
        textChannelManager.setTopic("게임이나 활동을 함께할 사람들을 이 채널에서 모집하세요. ✅를 눌러 참여하거나 취소하세요.").queue();

        Role everyoneRole = textChannel.getGuild().getPublicRole();
        Member botMember = textChannel.getGuild().getSelfMember();

        textChannel.upsertPermissionOverride(everyoneRole)
                .deny(Permission.MESSAGE_SEND)
                .queue(success -> {
                    logger.info("Permission denied for everyone role on recruit channel.");
                }, failure -> {
                    logger.error("Failed to deny permission for everyone role on recruit channel.");
                });

        textChannel.upsertPermissionOverride(botMember)
                .grant(Permission.MESSAGE_MANAGE)
                .queue(success -> {
                    logger.info("Permission granted for bot on recruit channel.");
                }, failure -> {
                    logger.error("Failed to grant permission for bot on recruit channel.");
                });
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
