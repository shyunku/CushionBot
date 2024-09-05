package service.recruit;

import Utilities.TextStyler;
import Utilities.TimeUtil;
import Utilities.Util;
import exceptions.RecruitPublishException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.slf4j.Logger;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Recruit {
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(Recruit.class);

    private final String key;
    private String gameName;
    private long recruitNum;

    private long registeredAt;
    private long recruitAt;
    private long duration;

    private Guild guild;
    private Member registerer;
    private Set<Member> participants = new HashSet<>();
    private Message message = null;

    public boolean isDead = false;
    private long timeLeftUntilNotify = 0;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> notifyFuture;
    private ScheduledFuture<?> destroyFuture;

    public Recruit(Guild guild, String key, Member registerer, String gameName, long recruitNum, long registeredAt, long recruitAt, long duration) {
        this.guild = guild;
        this.key = key;
        this.registerer = registerer;
        this.gameName = gameName;
        this.recruitNum = recruitNum;
        this.registeredAt = registeredAt;
        this.recruitAt = recruitAt;
        this.duration = duration;
    }

    public Message publish(TextChannel channel, Member publisher) throws RecruitPublishException {
        if (channel == null || message != null) {
            throw new RecruitPublishException("Recruit message already published or channel is null.");
        }

        MessageEmbed embed = getEmbed();
        this.message = channel.sendMessage("@everyone").setEmbeds(embed).complete();
        this.message.addReaction(Emoji.fromUnicode("✅")).queue();
        this.addParticipant(publisher);

        this.setNotifySchedule();
        this.setDestroySchedule();
        if (recruitAt != 0) {
            new Thread(() -> {
                while (!isDead) {
                    try {
                        Thread.sleep(60 * 1000);
                        if (timeLeftUntilNotify > 0) {
                            timeLeftUntilNotify = Math.max(0, timeLeftUntilNotify - 60 * 1000);
                        }
                        updateContent();
                    } catch (InterruptedException e) {
                        logger.warn("Recruit update thread interrupted.");
                    }
                }
            }).start();
        }

        return this.message;
    }

    public boolean addParticipant(Member member) {
        // reject if participants are full
        if (recruitNum > 0 && participants.size() >= recruitNum) return false;

        // add member to participants
        participants.add(member);

        // update content
        updateContent();

        return true;
    }

    public void removeParticipant(Member member) {
        // remove member from participants
        participants.remove(member);

        // update content
        updateContent();
    }

    private void updateContent() {
        if (message == null) return;
        message.editMessageEmbeds(getEmbed()).queue(success -> {
        }, failure -> {
            logger.error("Failed to update recruit message: {}", failure.getMessage());
        });
    }

    private MessageEmbed getEmbed() {
        boolean isRecruitDone = recruitNum > 0 && participants.size() >= recruitNum;

        boolean isStarted = recruitAt != 0 && System.currentTimeMillis() > recruitAt;

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle(String.format("%s%s", gameName, isStarted ? " (모집 종료)" : (isRecruitDone ? " (모집 완료)" : " (모집 중)")));
        // refer everyone
        embedBuilder.setDescription("같이하실 분을 모집합니다.\n");
        embedBuilder.setColor(isStarted ? 0x444444 : 0x3D99FF);
        embedBuilder.addField("구인코드", TextStyler.Block(key), true);
        if (registerer != null) embedBuilder.addField("등록자", registerer.getAsMention(), true);
        if (recruitAt != 0) {
            embedBuilder.addField("모집/시작시간", TextStyler.Block(recruitTime()), false);
            if (recruitAt > System.currentTimeMillis()) {
                embedBuilder.addField("시작까지 남은 시간", TextStyler.Blockf("%s 남음", Util.getRemainTimeMinute(recruitAt)), true);
            }
//            if (timeLeftUntilNotify > 0) {
//                embedBuilder.addField("알림까지 남은 시간", TextStyler.Blockf("%s 남음", Util.getDurationStringMinute(timeLeftUntilNotify)), true);
//            }
        }

        StringBuilder sb = new StringBuilder();
        for (Member member : participants) {
            sb.append(member.getAsMention()).append("\n");
        }

        if (sb.length() == 0) sb.append("현재 참여자 없음");
        embedBuilder.addField(String.format("참여자 %s",
                recruitNum > 0 ? String.format("(%d/%d)", participants.size(), recruitNum)
                        : String.format("(%d명)", participants.size())
        ), sb.toString(), false);
        return embedBuilder.build();
    }

    private String recruitTime() {
        if (recruitAt == 0) return "지금 바로";
        Calendar calendar = TimeUtil.getKstCalendar();
        calendar.setTimeInMillis(recruitAt);
        boolean isToday = calendar.get(Calendar.DAY_OF_MONTH) == Calendar.getInstance().get(Calendar.DAY_OF_MONTH);

        return String.format("%d일(%s) %d시 %02d분",
                calendar.get(Calendar.DAY_OF_MONTH),
                isToday ? "오늘" : "내일",
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE));
    }

    private void setNotifySchedule() {
        // 기존 notify 스케줄이 있으면 취소
        if (notifyFuture != null && !notifyFuture.isCancelled()) {
            notifyFuture.cancel(true);
        }

        // notify when recruit is almost done
        if (recruitAt != 0) {
            long notifyTime = 5 * 60 * 1000;
            long timeLeft = recruitAt - System.currentTimeMillis();
            this.timeLeftUntilNotify = timeLeft - notifyTime;

            if (timeLeftUntilNotify > 0 && this.message != null) {
                // 새 알림 스케줄 예약 및 ScheduledFuture 객체 저장
                notifyFuture = scheduler.schedule(this::notifyUsers, timeLeftUntilNotify, TimeUnit.MILLISECONDS);

                logger.info("Recruit notify schedule set for: {} in {} seconds", gameName, timeLeftUntilNotify / 1000);
            } else {
                logger.info("Recruit notify schedule not set for: {} seconds, message_null = {}", timeLeftUntilNotify / 1000, message == null);
            }
        }
    }

    private void setDestroySchedule() {
        if (destroyFuture != null && !destroyFuture.isCancelled()) {
            destroyFuture.cancel(true);
        }

        // set scheduler
        long destroyDelay = (recruitAt == 0 ? registeredAt : recruitAt) + duration - System.currentTimeMillis();
        if (destroyDelay > 0) {
            destroyFuture = scheduler.schedule(this::destroy, destroyDelay, TimeUnit.MILLISECONDS);
            logger.info("Recruit destroy schedule set for: {} in {} seconds", key, destroyDelay / 1000);
        } else {
            logger.warn("Destroy schedule not set for: {} seconds as the delay is negative.", destroyDelay / 1000);
        }
    }

    private void notifyUsers() {
        if (message != null) {
            TextChannel channel = message.getChannel().asTextChannel();
            StringBuilder sb = new StringBuilder();
            sb.append(TextStyler.Bold("[알림] "));
            sb.append(String.format("%s 이벤트가 5분 후 시작/모집종료됩니다. 아래 참여자들은 준비해주세요.", gameName));
            sb.append("\n");
            for (Member member : participants) {
                sb.append(member.getAsMention()).append(" ");
            }
            channel.sendMessage(sb.toString()).queue(m -> {
                m.delete().queueAfter(5, java.util.concurrent.TimeUnit.MINUTES);
            });
        }
    }

    public void destroy() {
        // remove message
        if (message != null) {
            TextChannel channel = message.getChannel().asTextChannel();
            channel.deleteMessageById(message.getId()).queue();
        }

        // set dead flag
        isDead = true;
        scheduler.shutdown();
    }

    public void setRecruitAt(long recruitAt) {
        this.recruitAt = recruitAt;

        // reset time left until notify
        setNotifySchedule();

        // reset destroy schedule
        setDestroySchedule();

        // update content
        updateContent();
    }

    public void setRecruitNum(long recruitNum) {
        this.recruitNum = recruitNum;

        // update content
        updateContent();
    }

    public String getMessageId() {
        if (message == null) return null;
        return message.getId();
    }

    public String getGameName() {
        return gameName;
    }

    public String getKey() {
        return key;
    }
}
