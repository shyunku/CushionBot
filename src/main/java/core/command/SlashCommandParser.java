package core.command;

import Utilities.*;
import core.Service;
import core.Version;
import dtos.teamgg.SetSummonerLineFavorRequestDto;
import exceptions.InvalidLolStartTimeException;
import exceptions.PermissionInsufficientException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.modals.Modal;
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
import service.watcher.GuildWatcher;

import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
        e.deferReply(true).queue();
        e.getJDA().retrieveApplicationInfo().queue(info -> {
            boolean owner = info.getOwner().getId().equals(e.getUser().getId())
                    || (info.getTeam() != null && info.getTeam().isMember(e.getUser()));
            if (!owner) {
                e.getHook().sendMessage("봇 주인만 사용할 수 있는 명령어입니다.").queue();
                return;
            }
            // answer first: the process exits once the boxes are removed
            e.getHook().sendMessage("점검 완료 처리합니다. 봇이 다시 시작됩니다.").complete();
            Service.finishMaintenance(e.getJDA());
        }, failure -> e.getHook().sendMessage("봇 정보를 확인하지 못했습니다.").queue());
    }

    public void serverRanking(SlashCommandInteractionEvent e) {
        try {
            TextChannel textChannel = e.getChannel().asTextChannel();
            Guild guild = e.getGuild();
            if (guild == null) {
                this.sendReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.");
                return;
            }

            // same totals as the dashboard's 누적 ranking (whole kept record)
            List<Pair<String, Long>> guildRanking = new ArrayList<>();
            GuildWatcher.totals(guild.getId()).forEach((userId, total) -> guildRanking.add(Pair.of(userId, total)));
            if (guildRanking.isEmpty()) {
                this.sendReply(e, "서버 랭킹 정보가 없습니다.");
                return;
            }

            guildRanking.sort((a, b) -> Long.compare(b.getRight(), a.getRight()));
            List<Pair<String, Long>> top = guildRanking.subList(0, Math.min(5, guildRanking.size()));

            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("서버 랭킹");
            embedBuilder.setDescription("최근 1년간 서버 내 음성채널에 연결한 총 시간을 기준으로 정렬됩니다.");
            embedBuilder.setColor(new Color(104, 53, 255));

            for (int i = 0; i < top.size(); i++) {
                Pair<String, Long> pair = top.get(i);
                String userId = pair.getLeft();
                long totalDuration = pair.getRight();
                String durationStr = Util.getDurationString(totalDuration);
                embedBuilder.addField(String.format("[%d위]", i + 1), String.format("<@%s> %s", userId, TextStyler.Block(durationStr)), false);
            }

            MessageEmbed embed = embedBuilder.build();
            e.replyEmbeds(embed).queue();
        } catch (Exception err) {
            this.handleError(e, err);
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

            // same totals as the dashboard's 누적 ranking (whole kept record)
            List<Pair<String, Long>> guildRanking = new ArrayList<>();
            GuildWatcher.totals(guild.getId()).forEach((userId, total) -> guildRanking.add(Pair.of(userId, total)));
            if (guildRanking.isEmpty()) {
                this.sendReply(e, "서버 랭킹 정보가 없습니다.");
                return;
            }

            guildRanking.sort((a, b) -> Long.compare(b.getRight(), a.getRight()));
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
            this.handleError(e, err);
        }
    }

    public void clear(SlashCommandInteractionEvent e) {
        Member author = e.getMember();
        if (author == null || !this.isManager(author)) {
            this.sendVolatileEphemeralReply(e, "관리자만 메시지를 삭제할 수 있습니다.", 5);
            return;
        }
        OptionMapping amountOpt = e.getOption("message_count");
        int amount = Math.max(1, Math.min(300, amountOpt == null ? 1 : amountOpt.getAsInt()));
        MessageChannel channel = e.getChannel();

        e.deferReply().queue();
        // Discord returns at most 100 messages per request
        MessageHistory history = channel.getHistory();
        List<Message> collected = new ArrayList<>();
        retrievePages(history, amount, collected, () -> {
            channel.purgeMessages(collected);
            String boldStr = TextStyler.Bold("최근 메시지 " + collected.size() + "개가 " + TextStyler.member(author) + "에 의해 삭제 요청되었습니다.");
            e.getHook().sendMessage(boldStr).queue();
        }, failure -> {
            logger.error("Could not read messages to clear", failure);
            e.getHook().sendMessage("메시지를 불러오지 못했습니다.").queue();
        });
    }

    private void retrievePages(MessageHistory history, int remaining, List<Message> collected, Runnable done, java.util.function.Consumer<Throwable> failed) {
        if (remaining <= 0) {
            done.run();
            return;
        }
        history.retrievePast(Math.min(100, remaining)).queue(page -> {
            collected.addAll(page);
            if (page.isEmpty()) done.run();
            else retrievePages(history, remaining - page.size(), collected, done, failed);
        }, failed);
    }

    public void music(SlashCommandInteractionEvent e) {
        try {
            Guild guild = e.getGuild();
            if (guild == null) {
                this.sendVolatileReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.", 5);
                return;
            }
            String guildId = guild.getId();
            if (!this.isManager(e.getMember())) {
                this.sendVolatileEphemeralReply(e, "관리자만 음악 채널을 지정할 수 있습니다.", 5);
                return;
            }
            if (e.getChannel().getType() != ChannelType.TEXT) {
                this.sendVolatileEphemeralReply(e, "일반 텍스트 채널에서만 지정할 수 있습니다.", 5);
                return;
            }
            TextChannel textChannel = e.getChannel().asTextChannel();
            Service.addGuildManagerIfNotExists(guild);
            MusicBox musicBox = Service.GetMusicBoxByGuildId(guildId);
            musicBox.setMusicChannel(textChannel);

            MessageEmbed embed = musicBox.getInitialSettingEmbed();
            if (embed == null) {
                this.sendVolatileEphemeralReply(e, "음악 서버에 연결하지 못해 음악 채널을 지정하지 못했습니다.", 8);
                return;
            }
            e.replyEmbeds(embed).queue(interactionHook -> {
                interactionHook.retrieveOriginal().queue(musicBox::setMusicBoxMessage);
            });
        } catch (Exception err) {
            this.handleError(e, err);
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
            if (musicStreamer == null) {
                this.sendVolatileEphemeralReply(e, "음악 채널이 설정되지 않았습니다. /음악채널 명령어로 설정해주세요.", 5);
                return;
            }
            musicStreamer.shuffleTracksOnQueue();
            musicBox.updateEmbed();
            this.sendVolatileReply(e, "음악이 셔플되었습니다.", 5);
        } catch (Exception err) {
            this.handleError(e, err);
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
            if (musicStreamer == null) {
                this.sendVolatileEphemeralReply(e, "음악 채널이 설정되지 않았습니다. /음악채널 명령어로 설정해주세요.", 5);
                return;
            }
            OptionMapping volumeOpt = e.getOption("볼륨");
            if (volumeOpt == null) {
                this.sendVolatileReply(e, "볼륨을 입력해주세요.", 5);
                return;
            }
            int volume = musicStreamer.setVolume(volumeOpt.getAsInt());
            musicBox.updateEmbed();
            this.sendVolatileReply(e, String.format("볼륨이 %d%%로 설정되었습니다. 몇 초내에 적용됩니다.", volume), 5);
        } catch (Exception err) {
            this.handleError(e, err);
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
            if (!this.isManager(e.getMember())) {
                this.sendVolatileEphemeralReply(e, "관리자만 내전 채널을 지정할 수 있습니다.", 5);
                return;
            }
            if (e.getChannel().getType() != ChannelType.TEXT) {
                this.sendVolatileEphemeralReply(e, "일반 텍스트 채널에서만 지정할 수 있습니다.", 5);
                return;
            }
            TextChannel textChannel = e.getChannel().asTextChannel();
            Service.addGuildManagerIfNotExists(guild);
            LolBox lolBox = Service.GetLolBoxByGuildId(guildId);
            lolBox.setLolChannel(textChannel);

            MessageEmbedProps embedPair = lolBox.getEmbed();
            ReplyCallbackAction action = e.replyEmbeds(embedPair.messageEmbed);
            for (ActionRow actionRow : embedPair.actionRows) {
                action = action.addComponents(actionRow);
            }
            action.queue(interactionHook -> {
                interactionHook.retrieveOriginal().queue(lolBox::setLolBoxMessage);
            });
        } catch (Exception err) {
            this.handleError(e, err);
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
            this.handleError(e, err);
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
            this.handleError(e, err);
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
            this.handleError(e, err);
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
            List<String> joiners = lolBox.getJoiners();
            String remainTime = Util.getRelativeTime(startTime);
            StringBuilder sb = new StringBuilder();

            if (startTime > System.currentTimeMillis()) {
                sb.append(String.format("내전이 %s 뒤에 시작합니다. 태그되는 참여자 분들은 준비해주세요.\n", remainTime));
            } else {
                sb.append("내전 일정에 등록된 시간이 되었습니다. 태그되는 참여자 분들은 준비해주세요.\n");
            }
            for (String userId : joiners) {
                sb.append("<@").append(userId).append("> ");
            }
            e.reply(sb.toString()).queue();
        } catch (PermissionInsufficientException exception) {
            this.sendVolatileReply(e, "내전 참여자를 호출할 권한이 없습니다.", 8);
        } catch (Exception err) {
            this.handleError(e, err);
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
                e.deferReply().queue();
                guild.createTextChannel("구인채널").queue(textChannel -> {
                    RedisClient.set(recruitChannelKey, textChannel.getId());
                    recruitManager.recruitChannel = textChannel;
                    this.setRecruitChannel(textChannel);
                    e.getHook().sendMessage(String.format("구인 채널이 지정되었습니다. 이제 %s 채널에서 구인 정보를 확인하실 수 있습니다.", textChannel.getAsMention())).queue();
                }, failure -> e.getHook().sendMessage("구인 채널을 만들지 못했습니다. 봇의 채널 관리 권한을 확인해주세요.").queue());
            } else {
                if (channelOpt.getChannelType() != ChannelType.TEXT) {
                    this.sendVolatileEphemeralReply(e, "일반 텍스트 채널만 구인 채널로 지정할 수 있습니다.", 5);
                    return;
                }
                TextChannel textChannel = channelOpt.getAsChannel().asTextChannel();

                // save channel
                RedisClient.set(recruitChannelKey, textChannel.getId());
                recruitManager.recruitChannel = textChannel;
                this.setRecruitChannel(textChannel);
                e.reply(String.format("구인 채널이 지정되었습니다. 이제 %s 채널에서 구인 정보를 확인하실 수 있습니다.", textChannel.getAsMention())).queue();
            }
        } catch (PermissionInsufficientException exception) {
            this.sendVolatileEphemeralReply(e, "관리자만 구인 채널을 지정할 수 있습니다.", 5);
        } catch (Exception err) {
            this.handleError(e, err);
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

            TextInput gameNameInput = TextInput.create("gameName", TextInputStyle.SHORT)
                    .setPlaceholder("게임 이름/이벤트 이름 (ex. 자랭)")
                    .setRequired(true)
                    .build();
            TextInput recruitingNumInput = TextInput.create("recruitNum", TextInputStyle.SHORT)
                    .setPlaceholder("모집 인원 (ex. 5)")
                    .setRequired(false)
                    .build();
            TextInput timeInput = TextInput.create("time", TextInputStyle.SHORT)
                    .setPlaceholder("모집 시간 (ex. 21:30)")
                    .setRequired(false)
                    .build();
            TextInput durationInput = TextInput.create("duration", TextInputStyle.SHORT)
                    .setPlaceholder("소요 시간 (ex. 1시간 반 -> 1.5)")
                    .setRequired(false)
                    .build();

            Modal modal = Modal.create("recruitModal", "구인 등록")
                    .addComponents(Label.of("게임 이름/이벤트 이름을 입력해주세요.", gameNameInput))
                    .addComponents(Label.of("모집 인원을 숫자로 입력해주세요. (옵션)", recruitingNumInput))
                    .addComponents(Label.of("모집 시간을 입력해주세요. 현재 시각보다 전일 경우 내일로 설정됩니다. (옵션)", timeInput))
                    .addComponents(Label.of("예상 소요 시간을 입력해주세요. (옵션, 1시간 단위)", durationInput))
                    .build();

            e.replyModal(modal).queue();
        } catch (Exception err) {
            this.handleError(e, err);
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
                if (!recruitManager.unregisterRecruit(member, null)) {
                    this.sendVolatileReply(e, "취소할 구인이 없습니다.", 5);
                    return;
                }
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
            this.handleError(e, err);
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
                long recruitAt;
                try {
                    LocalTime time = LocalTime.parse(timeInput.getAsString().trim(), DateTimeFormatter.ofPattern("H:mm"));
                    // same rule as registration: a time that has already passed today means tomorrow
                    recruitAt = Recruit.nextKstTime(time.getHour(), time.getMinute());
                } catch (DateTimeParseException e2) {
                    this.sendVolatileEphemeralReply(e, "시간 형식이 올바르지 않습니다. (ex. 21:30)", 5);
                    return;
                }

                String recruitKey = recruitKeyInput.getAsString();
                boolean success = recruitManager.changeRecruitTime(member, recruitKey, recruitAt);
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
            this.handleError(e, err);
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
            boolean mayMentionEveryone = e.getGuildChannel() != null
                    && member.hasPermission(e.getGuildChannel(), Permission.MESSAGE_MENTION_EVERYONE);
            if (mayMentionEveryone) e.reply("@everyone").setEmbeds(eb.build()).queue();
            else e.replyEmbeds(eb.build()).queue();
        } catch (Exception err) {
            this.handleError(e, err);
        }
    }

    /**
     * team.gg account link: team.gg issues a one-time Riot login link for this Discord user (valid 10 minutes),
     * shown only to the requester; the result is polled and reported back in the same ephemeral thread.
     */
    public void teamggRegister(SlashCommandInteractionEvent e) {
        if (e.getGuild() == null) {
            this.sendVolatileReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.", 5);
            return;
        }
        String userId = e.getUser().getId();
        e.deferReply(true).queue();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            Map<?, ?> flow;
            try {
                flow = Request.post(TEAMGG_API + "/v1/auth/rso/discord/start", Map.of("discordId", userId), Map.class, TokenManager.TEAMGG_BOT_API_KEY);
            } catch (Exception err) {
                logger.error("team.gg link start failed", err);
                e.getHook().sendMessage(teamggErrorMessage(err)).queue();
                return;
            }
            Object url = flow == null ? null : flow.get("authorizeUrl");
            Object flowId = flow == null ? null : flow.get("flowId");
            if (url == null) {
                e.getHook().sendMessage("연동 링크를 받지 못했습니다. 잠시 후 다시 시도해주세요.").queue();
                return;
            }
            e.getHook().sendMessage(String.format("아래 링크로 라이엇 계정에 로그인하면 디스코드와 연동됩니다. (10분 동안 한 번만 사용 가능)\n%s", url)).queue();
            if (flowId != null) pollTeamggLink(e, String.valueOf(flowId), 0);
        });
    }

    private static final java.util.concurrent.ScheduledExecutorService teamggPoller =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "teamgg-link-poll");
                t.setDaemon(true);
                return t;
            });
    private static final int TEAMGG_POLL_SECONDS = 5;
    /** The link lives 10 minutes; poll a little longer than that. */
    private static final int TEAMGG_POLL_LIMIT = 11 * 60 / TEAMGG_POLL_SECONDS;

    /** team.gg's API host (teamgg.kr itself serves the web app and answers every path with HTML). */
    private static final String TEAMGG_API = "https://api.teamgg.kr";

    private void pollTeamggLink(SlashCommandInteractionEvent e, String flowId, int attempt) {
        if (attempt >= TEAMGG_POLL_LIMIT) return;
        teamggPoller.schedule(() -> {
            try {
                Map<?, ?> status = Request.get(TEAMGG_API + "/v1/auth/rso/status?flowId=" + flowId, Map.class);
                String state = status == null ? "pending" : String.valueOf(status.get("status"));
                if (state.equals("complete")) {
                    Object name = status.get("riotDisplayName");
                    e.getHook().sendMessage(String.format("연동 완료: %s 계정이 디스코드와 연동되었습니다.", name == null ? "라이엇" : name)).queue();
                    return;
                }
                if (state.equals("error")) {
                    Object message = status.get("error");
                    e.getHook().sendMessage("연동 실패: " + (message == null ? "알 수 없는 오류" : message)).queue();
                    return;
                }
            } catch (Request.HttpStatusException err) {
                // team.gg answers 404 once the login link has expired
                if (err.status == 404) {
                    e.getHook().sendMessage("연동 링크가 만료되었습니다. /팀지지등록 으로 다시 시도해주세요.").queue();
                    return;
                }
                logger.warn("team.gg link status check failed: {}", err.getMessage());
            } catch (Exception err) {
                logger.warn("team.gg link status check failed: {}", err.getMessage());
            }
            pollTeamggLink(e, flowId, attempt + 1);
        }, TEAMGG_POLL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static String teamggErrorMessage(Exception err) {
        if (err instanceof Request.HttpStatusException) {
            int status = ((Request.HttpStatusException) err).status;
            if (status == 401) return "team.gg 연동 키가 맞지 않습니다. 관리자에게 알려주세요.";
            if (status == 404) return "team.gg 연동 서버가 아직 준비되지 않았습니다. 잠시 후 다시 시도해주세요.";
        }
        return "team.gg 서버에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.";
    }

    public void teamggLineFavor(SlashCommandInteractionEvent e) {
        try {
            Guild guild = e.getGuild();
            if (guild == null) {
                this.sendVolatileReply(e, "이 명령어는 guild 내에서만 사용 가능합니다.", 5);
                return;
            }
            String guildId = guild.getId();
            User user = e.getUser();
            Member member = e.getMember();
            if (member == null) {
                this.sendVolatileReply(e, "멤버를 식별할 수 없습니다.", 5);
                return;
            }

            OptionMapping topFavorInput = e.getOption("탑");
            OptionMapping jungleFavorInput = e.getOption("정글");
            OptionMapping midFavorInput = e.getOption("미드");
            OptionMapping adcFavorInput = e.getOption("원딜");
            OptionMapping supportFavorInput = e.getOption("서폿");

            if (topFavorInput == null || jungleFavorInput == null || midFavorInput == null || adcFavorInput == null || supportFavorInput == null) {
                this.sendVolatileEphemeralReply(e, "누락된 선호도가 있습니다.", 5);
                return;
            }

            int topFavor = topFavorInput.getAsInt();
            int jungleFavor = jungleFavorInput.getAsInt();
            int midFavor = midFavorInput.getAsInt();
            int adcFavor = adcFavorInput.getAsInt();
            int supportFavor = supportFavorInput.getAsInt();

            ArrayList<Integer> favorIntList = new ArrayList<>();
            favorIntList.add(topFavor);
            favorIntList.add(jungleFavor);
            favorIntList.add(midFavor);
            favorIntList.add(adcFavor);
            favorIntList.add(supportFavor);

            for (int favor : favorIntList) {
                if (favor < -1 || favor > 2) {
                    this.sendVolatileEphemeralReply(e, "선호도는 -1, 0, 1, 2 중 하나로 입력해주세요.", 5);
                    return;
                }
            }
            if (favorIntList.size() != 5) {
                this.sendVolatileEphemeralReply(e, "선호도가 올바른 배열의 형태가 아닙니다. (탑, 정글, 미드, 원딜, 서폿 순)", 5);
                return;
            }

            int[] favorArray = new int[5];
            for (int i = 0; i < 5; i++) {
                favorArray[i] = favorIntList.get(i);
            }

            e.deferReply().queue();
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                // check the Riot account link first, then save
                try {
                    ArrayList<?> results = Request.get(String.format(TEAMGG_API + "/v1/api/discordIntegrations?token=%s", user.getId()), ArrayList.class, TokenManager.TEAMGG_BOT_API_KEY);
                    if (results == null || results.isEmpty()) {
                        e.getHook().sendMessage("라이엇 계정이 연동되지 않았습니다. 먼저 /팀지지등록 명령어로 등록해주세요.").setEphemeral(true).queue();
                        return;
                    }
                } catch (Exception err) {
                    logger.error("team.gg link check failed", err);
                    e.getHook().sendMessage("등록 실패: " + teamggErrorMessage(err)).setEphemeral(true).queue();
                    return;
                }
                try {
                    SetSummonerLineFavorRequestDto requestDto = new SetSummonerLineFavorRequestDto(user.getId(), favorArray);
                    Request.post(TEAMGG_API + "/v1/api/summonerLineFavor", requestDto, String.class, TokenManager.TEAMGG_BOT_API_KEY);
                    e.getHook().sendMessage(String.format("%s님의 라인 선호도가 탑(%d) 정글(%d) 미드(%d) 원딜(%d) 서폿(%d)로 설정되었습니다.",
                            member.getAsMention(), favorArray[0], favorArray[1], favorArray[2], favorArray[3], favorArray[4]
                    )).queue();
                } catch (Exception err) {
                    logger.error("team.gg line favor failed", err);
                    e.getHook().sendMessage("선호도 등록 실패: 서버 오류가 발생했습니다.").setEphemeral(true).queue();
                }
            });
        } catch (Exception err) {
            this.handleError(e, err);
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

    private void sendVolatileEphemeralReply(SlashCommandInteractionEvent e, String message, int delay) {
        e.reply(message).setEphemeral(true).queue(interactionHook -> {
            interactionHook.retrieveOriginal().queue(message1 -> {
                message1.delete().queueAfter(delay, java.util.concurrent.TimeUnit.SECONDS);
            });
        });
    }

    private boolean isManager(Member member) {
        return member != null && (member.isOwner() || member.hasPermission(Permission.ADMINISTRATOR));
    }

    /** Log and tell the user, unless the interaction was already answered. */
    private void handleError(SlashCommandInteractionEvent e, Exception err) {
        logger.error("Command /{} failed", e.getName(), err);
        String message = "명령을 처리하는 중 오류가 발생했습니다.";
        if (!e.isAcknowledged()) e.reply(message).setEphemeral(true).queue();
        else e.getHook().sendMessage(message).setEphemeral(true).queue();
    }
}
