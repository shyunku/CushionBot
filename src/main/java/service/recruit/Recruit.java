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
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class Recruit {
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(Recruit.class);
    /** One small shared scheduler for every recruit (notify, end, minute refresh). */
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "recruit-scheduler");
        t.setDaemon(true);
        return t;
    });
    private static final long NOTIFY_BEFORE_MS = 5 * 60 * 1000;

    private final String key;
    private final String gameName;
    private volatile long recruitNum;

    private final long registeredAt;
    private volatile long recruitAt;
    private final long duration;

    private final Guild guild;
    private final String registererId;
    /** Participant user ids (members may be uncached, so ids are what events reliably give). */
    private final Set<String> participants = ConcurrentHashMap.newKeySet();
    private volatile Message message = null;

    public volatile boolean isDead = false;
    private Consumer<Recruit> onDead = r -> {
    };

    private ScheduledFuture<?> notifyFuture;
    private ScheduledFuture<?> destroyFuture;
    private ScheduledFuture<?> refreshFuture;

    public Recruit(Guild guild, String key, Member registerer, String gameName, long recruitNum, long registeredAt, long recruitAt, long duration) {
        this.guild = guild;
        this.key = key;
        this.registererId = registerer == null ? null : registerer.getId();
        this.gameName = gameName;
        this.recruitNum = recruitNum;
        this.registeredAt = registeredAt;
        this.recruitAt = recruitAt;
        this.duration = duration;
    }

    /** Called once when the recruit ends, so the manager can forget it. */
    public void setOnDead(Consumer<Recruit> onDead) {
        this.onDead = onDead;
    }

    /**
     * Post the recruit. {@code mentionEveryone} should only be true when the publisher may mention
     * everyone in that channel themselves.
     */
    public Message publish(TextChannel channel, Member publisher, boolean mentionEveryone) throws RecruitPublishException {
        if (channel == null || message != null) {
            throw new RecruitPublishException("Recruit message already published or channel is null.");
        }

        MessageEmbed embed = getEmbed();
        try {
            this.message = mentionEveryone
                    ? channel.sendMessage("@everyone").setEmbeds(embed).complete()
                    : channel.sendMessageEmbeds(embed).complete();
        } catch (RuntimeException e) {
            throw new RecruitPublishException("구인 채널에 메시지를 보낼 수 없습니다. 채널이 삭제되었거나 권한이 없습니다.");
        }
        this.message.addReaction(Emoji.fromUnicode("✅")).queue();
        this.addParticipant(publisher.getId());

        this.setNotifySchedule();
        this.setDestroySchedule();
        if (recruitAt != 0) {
            refreshFuture = scheduler.scheduleAtFixedRate(this::updateContent, 1, 1, TimeUnit.MINUTES);
        }
        return this.message;
    }

    public boolean addParticipant(String userId) {
        if (recruitNum > 0 && participants.size() >= recruitNum && !participants.contains(userId)) return false;
        participants.add(userId);
        updateContent();
        return true;
    }

    public void removeParticipant(String userId) {
        participants.remove(userId);
        updateContent();
    }

    private void updateContent() {
        Message m = message;
        if (m == null || isDead) return;
        m.editMessageEmbeds(getEmbed()).queue(success -> {
        }, failure -> logger.error("Failed to update recruit message: {}", failure.getMessage()));
    }

    private MessageEmbed getEmbed() {
        boolean isRecruitDone = recruitNum > 0 && participants.size() >= recruitNum;
        boolean isStarted = recruitAt != 0 && System.currentTimeMillis() > recruitAt;

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle(String.format("%s%s", gameName, isStarted ? " (모집 종료)" : (isRecruitDone ? " (모집 완료)" : " (모집 중)")));
        embedBuilder.setDescription("같이하실 분을 모집합니다.\n");
        embedBuilder.setColor(isStarted ? 0x444444 : 0x3D99FF);
        embedBuilder.addField("구인코드", TextStyler.Block(key), true);
        if (registererId != null) embedBuilder.addField("등록자", "<@" + registererId + ">", true);
        if (recruitAt != 0) {
            embedBuilder.addField("모집/시작시간", TextStyler.Block(recruitTime()), false);
            if (recruitAt > System.currentTimeMillis()) {
                embedBuilder.addField("시작까지 남은 시간", TextStyler.Blockf("%s 남음", Util.getRemainTimeMinute(recruitAt)), true);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String userId : participants) {
            String line = "<@" + userId + ">\n";
            // embed field values are limited to 1024 characters
            if (sb.length() + line.length() > MessageEmbed.VALUE_MAX_LENGTH - 20) {
                sb.append("외 ").append(participants.size() - sb.toString().split("\n").length).append("명");
                break;
            }
            sb.append(line);
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
        Calendar today = TimeUtil.getKstCalendar();
        boolean isToday = calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
        int HH = calendar.get(Calendar.HOUR_OF_DAY);
        int hh = (HH + 11) % 12 + 1;
        String ampm = HH < 12 ? "오전" : "오후";

        return String.format("%d일(%s) %s %d시 %02d분",
                calendar.get(Calendar.DAY_OF_MONTH),
                isToday ? "오늘" : "내일",
                ampm, hh,
                calendar.get(Calendar.MINUTE));
    }

    private synchronized void setNotifySchedule() {
        if (notifyFuture != null) notifyFuture.cancel(false);
        if (recruitAt == 0 || message == null || isDead) return;
        long delay = recruitAt - System.currentTimeMillis() - NOTIFY_BEFORE_MS;
        if (delay > 0) {
            notifyFuture = scheduler.schedule(this::notifyUsers, delay, TimeUnit.MILLISECONDS);
            logger.info("Recruit notify schedule set for: {} in {} seconds", gameName, delay / 1000);
        }
    }

    private synchronized void setDestroySchedule() {
        if (destroyFuture != null) destroyFuture.cancel(false);
        if (isDead) return;
        long destroyDelay = (recruitAt == 0 ? registeredAt : recruitAt) + duration - System.currentTimeMillis();
        // a recruit whose end has already passed ends now instead of staying forever
        destroyFuture = scheduler.schedule(this::destroy, Math.max(0, destroyDelay), TimeUnit.MILLISECONDS);
        logger.info("Recruit destroy schedule set for: {} in {} seconds", key, Math.max(0, destroyDelay) / 1000);
    }

    private void notifyUsers() {
        Message m = message;
        if (m == null || isDead) return;
        StringBuilder sb = new StringBuilder();
        sb.append(TextStyler.Bold("[알림] "));
        sb.append(String.format("%s 이벤트가 5분 후 시작/모집종료됩니다. 아래 참여자들은 준비해주세요.", gameName));
        sb.append("\n");
        for (String userId : participants) {
            sb.append("<@").append(userId).append("> ");
        }
        m.getChannel().sendMessage(sb.toString()).queue(sent -> sent.delete().queueAfter(5, TimeUnit.MINUTES),
                failure -> logger.error("Failed to send recruit notice: {}", failure.getMessage()));
    }

    public synchronized void destroy() {
        if (isDead) return;
        isDead = true;
        if (notifyFuture != null) notifyFuture.cancel(false);
        if (destroyFuture != null) destroyFuture.cancel(false);
        if (refreshFuture != null) refreshFuture.cancel(false);
        Message m = message;
        if (m != null) {
            m.delete().queue(null, failure -> logger.warn("Failed to delete recruit message: {}", failure.getMessage()));
        }
        onDead.accept(this);
    }

    public void setRecruitAt(long recruitAt) {
        this.recruitAt = recruitAt;
        setNotifySchedule();
        setDestroySchedule();
        updateContent();
    }

    public void setRecruitNum(long recruitNum) {
        this.recruitNum = recruitNum;
        updateContent();
    }

    public String getMessageId() {
        Message m = message;
        return m == null ? null : m.getId();
    }

    public String getGameName() {
        return gameName;
    }

    public String getKey() {
        return key;
    }

    /**
     * Next occurrence of hour:minute in KST: today, or tomorrow when that time has already passed.
     */
    public static long nextKstTime(int hour, int minute) {
        Calendar now = TimeUtil.getKstCalendar();
        Calendar at = TimeUtil.getKstCalendar();
        at.set(Calendar.HOUR_OF_DAY, hour);
        at.set(Calendar.MINUTE, minute);
        at.set(Calendar.SECOND, 0);
        at.set(Calendar.MILLISECOND, 0);
        if (at.before(now)) at.add(Calendar.DATE, 1);
        return at.getTimeInMillis();
    }
}
