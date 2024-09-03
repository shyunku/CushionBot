package service.recruit;

import Utilities.TextStyler;
import exceptions.RecruitPublishException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.slf4j.Logger;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class Recruit {
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(Recruit.class);

    private String gameName;
    private long recruitNum;

    private long registeredAt;
    private long recruitAt;
    private long duration;

    private Set<Member> participants = new HashSet<>();
    private Message message = null;

    public boolean isDead = false;

    public Recruit(String gameName, long recruitNum, long registeredAt, long recruitAt, long duration) {
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
        this.message = channel.sendMessageEmbeds(embed).complete();
        this.message.addReaction(Emoji.fromUnicode("✅")).queue();
        this.addParticipant(publisher);

        // notify when recruit is almost done
        if (recruitNum > 0) {
            long notifyTime = 5 * 60 * 1000;
            long timeLeft = recruitAt - System.currentTimeMillis();
            if (timeLeft > notifyTime && this.message != null) {
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        if (message != null) {
                            TextChannel channel = message.getChannel().asTextChannel();
                            StringBuilder sb = new StringBuilder();
                            sb.append(TextStyler.Bold("[알림] "));
                            sb.append(String.format("%s 이벤트가 5분 후 시작/모집종료됩니다. 참여자들은 준비해주세요.", gameName));
                            sb.append("\n");
                            for (Member member : participants) {
                                sb.append(member.getAsMention()).append(" ");
                            }
                            channel.sendMessage(sb.toString()).queue(m -> {
                                m.delete().queueAfter(5, java.util.concurrent.TimeUnit.MINUTES);
                            });
                        }
                    }
                }, timeLeft - notifyTime);
                logger.debug("Recruit notify schedule set for: {} in {}ms", gameName, timeLeft - notifyTime);
            }
        }

        this.setDestroySchedule();
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
        message.editMessageEmbeds(getEmbed()).queue();
    }

    private MessageEmbed getEmbed() {
        boolean isRecruitDone = recruitNum > 0 && participants.size() >= recruitNum;

        boolean isStarted = recruitAt != 0 && System.currentTimeMillis() > recruitAt;

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle(String.format("%s%s", gameName, isStarted ? " (모집 종료)" : (isRecruitDone ? " (모집 완료)" : " (모집 중)")));
        embedBuilder.setDescription("같이하실 분을 모집합니다.\n");
        embedBuilder.setColor(0x3D99FF);
        if (recruitAt != 0) embedBuilder.addField("모집/시작시간", TextStyler.Block(recruitTime()), false);

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
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(recruitAt);
        boolean isToday = calendar.get(Calendar.DAY_OF_MONTH) == Calendar.getInstance().get(Calendar.DAY_OF_MONTH);

        return String.format("%d일 (%s) %d시 %d분",
                calendar.get(Calendar.DAY_OF_MONTH),
                isToday ? "오늘" : "내일",
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE));
    }

    private void setDestroySchedule() {
        // set scheduler
        Calendar calendar = Calendar.getInstance();
        if (recruitAt == 0) calendar.setTimeInMillis(registeredAt);
        else calendar.setTimeInMillis(recruitAt);
        calendar.add(Calendar.MILLISECOND, (int) duration);

        // destroy recruit
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                destroy();
            }
        }, calendar.getTime());
    }

    public void destroy() {
        // remove message
        if (message != null) {
            TextChannel channel = message.getChannel().asTextChannel();
            channel.deleteMessageById(message.getId()).queue();
        }

        // set dead flag
        isDead = true;
    }

    public String getMessageId() {
        if (message == null) return null;
        return message.getId();
    }

    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    public long getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(long registeredAt) {
        this.registeredAt = registeredAt;
    }

    public long getRecruitAt() {
        return recruitAt;
    }

    public void setRecruitAt(long recruitAt) {
        this.recruitAt = recruitAt;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }
}
