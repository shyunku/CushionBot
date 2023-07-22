package service.leagueoflegends.Core;

import Utilities.TextStyler;
import Utilities.Util;
import exceptions.InvalidLolStartTimeException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.discord.ControlBox;
import service.discord.MessageEmbedProps;
import service.guild.core.GuildUtil;
import service.inmemory.RedisClient;

import java.awt.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

public class LolBox implements ControlBox {
    private final Logger logger = LoggerFactory.getLogger(LolBox.class);
    private Guild guild;
    private Message lolBoxMessage;
    private TextChannel lolChannel;
    private boolean isCollectingTeam = false;
    private long startTime = 0;

    private HashMap<Member, Pair<Boolean, Long>> answeredMembers = new HashMap<>();

    public LolBox(Guild guild, TextChannel lolChannel) {
        this.guild = guild;
        this.lolChannel = lolChannel;

        String chanKey = GuildUtil.lolChannelKey(guild.getId());
        if (RedisClient.has(chanKey)) {
            String chanId = RedisClient.get(chanKey);
            TextChannel channel = guild.getTextChannelById(chanId);
            if (channel != null) {
                this.lolChannel = channel;
            }
        }

        String msgKey = GuildUtil.lolBoxMessageKey(guild.getId());
        if (this.lolChannel != null && RedisClient.has(msgKey)) {
            String msgId = RedisClient.get(msgKey);
            try {
                Message message = this.lolChannel.retrieveMessageById(msgId).complete();
                if (message != null) {
                    this.lolBoxMessage = message;
                }
            } catch (ErrorResponseException e) {
                e.printStackTrace();
            }
        }

        // initial update embed
        this.updateEmbed();
    }

    public void startCollectingTeam(int time, int minute) throws InvalidLolStartTimeException {
        this.isCollectingTeam = true;
        this.answeredMembers.clear();
        this.setStartTime(time, minute);
    }

    public void addMemberAnswer(Member member, boolean join) {
        if (!this.isCollectingTeam) return;
        this.answeredMembers.put(member, Pair.of(join, System.currentTimeMillis()));
    }

    public void removeMemberAnswer(Member member) {
        if (!this.isCollectingTeam) return;
        this.answeredMembers.remove(member);
    }

    public void stopCollectingTeam() {
        this.isCollectingTeam = false;
        this.answeredMembers.clear();
        this.startTime = 0;
    }

    public MessageEmbedProps getEmbed() {
        MessageEmbedProps embedPair = new MessageEmbedProps();
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("LOL 내전 관리자");
        embedBuilder.setDescription(
                this.isCollectingTeam ?
                        "현재 인원 모집 중입니다. 아래 참여/불참 버튼을 눌러 투표해주세요." :
                        "현재는 내전 일정이 없습니다."
        );

        if (this.isCollectingTeam) {
            embedBuilder.setColor(new Color(31, 255, 45));
        } else {
            embedBuilder.setColor(new Color(88, 88, 88));
        }

        int totalMembers = this.guild.getMemberCount();
        int notAnsweredCnt = totalMembers - this.answeredMembers.size();

        int joinerCount = 0;
        for (Member member : this.answeredMembers.keySet()) {
            Pair<Boolean, Long> answer = this.answeredMembers.get(member);
            if (answer.getLeft()) {
                joinerCount++;
            }
        }
        int notJoiners = this.answeredMembers.size() - joinerCount;

        ActionRow controller = null;
        if (this.isCollectingTeam) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(this.startTime);
            String startTime = Util.timeFormat(
                    this.startTime,
                    String.format(
                            "%s (%s일) a hh시 mm분",
                            Util.dirtyRelativeDay(this.startTime),
                            cal.get(Calendar.DAY_OF_MONTH)
                    )
            );
            embedBuilder.addField("시작 시간", TextStyler.Block(startTime), false);

            embedBuilder.addField("참여자", TextStyler.Block(joinerCount + " 명"), true);
            embedBuilder.addField("불참자", TextStyler.Block(notJoiners + " 명"), true);
            embedBuilder.addField("무응답", TextStyler.Block(notAnsweredCnt + " 명"), true);

            ArrayList<Pair<Member, Long>> joiners = new ArrayList<>();
            ArrayList<Pair<Member, Long>> notJoinersList = new ArrayList<>();
            for (Member member : this.answeredMembers.keySet()) {
                Pair<Boolean, Long> answer = this.answeredMembers.get(member);
                if (answer.getLeft()) {
                    joiners.add(Pair.of(member, answer.getRight()));
                } else {
                    notJoinersList.add(Pair.of(member, answer.getRight()));
                }
            }

            StringBuilder sb = new StringBuilder();
            joiners.sort((a, b) -> (int) (a.getRight() - b.getRight()));
            for (int i = 0; i < joiners.size(); i++) {
                Pair<Member, Long> joiner = joiners.get(i);
                String time = Util.timeFormat(joiner.getRight(), "a hh시 mm분");
                sb.append(String.format("%d. %s (%s)", i + 1, String.format("<@%s>", joiner.getLeft().getId()), time));
                if (i != joiners.size() - 1) {
                    sb.append("\n");
                }
                if (i == 9) {
                    sb.append("---------------------------------\n");
                }
            }
            if (!joiners.isEmpty()) embedBuilder.addField("⭕ 내전 참여자 목록", sb.toString(), false);

            sb = new StringBuilder();
            notJoinersList.sort((a, b) -> (int) (a.getRight() - b.getRight()));
            for (int i = 0; i < notJoinersList.size(); i++) {
                Pair<Member, Long> notJoiner = notJoinersList.get(i);
                String time = Util.timeFormat(notJoiner.getRight(), "a hh시 mm분");
                sb.append(String.format("%d. %s (%s)", i + 1, String.format("<@%s>", notJoiner.getLeft().getId()), time));
                if (i != notJoinersList.size() - 1) {
                    sb.append("\n");
                }
            }
            if (!notJoinersList.isEmpty()) embedBuilder.addField("❌ 내전 불참자 목록", sb.toString(), false);


            controller = ActionRow.of(
                    Button.primary("lolJoin", String.format("참여 (%d)", joinerCount)),
                    Button.danger("lolNotJoin", String.format("불참 (%d)", notJoiners)),
                    Button.secondary("lolDontKnow", "무응답")
            );
        }

        embedPair.setMessageEmbed(embedBuilder.build());
        if (controller != null) {
            embedPair.addActionRow(controller);
        }
        return embedPair;
    }

    @Override
    public void updateEmbed() {
        MessageEmbedProps embed = this.getEmbed();
        if (this.lolBoxMessage == null) {
            embed.sendMessageEmbedWithHook(this.lolChannel, message -> {
                setLolBoxMessage(message);
                this.updateNonNullLolActionEmbed(embed);
            });
            return;
        }
        this.updateNonNullLolActionEmbed(embed);
    }

    private void updateNonNullLolActionEmbed(MessageEmbedProps embed) {
        assert this.lolBoxMessage != null;
        embed.editMessageEmbed(this.lolBoxMessage);
    }

    public void setLolChannel(TextChannel lolChannel) {
        if (lolChannel == null) return;
        this.lolChannel = lolChannel;
        RedisClient.set(GuildUtil.lolChannelKey(guild.getId()), lolChannel.getId());
    }

    public void setLolBoxMessage(Message lolBoxMessage) {
        if (lolBoxMessage == null) return;
        this.lolBoxMessage = lolBoxMessage;
        RedisClient.set(GuildUtil.lolBoxMessageKey(guild.getId()), lolBoxMessage.getId());
    }

    public TextChannel getLolChannel() {
        return lolChannel;
    }

    public HashMap<Member, Pair<Boolean, Long>> getAnsweredMembers() {
        return answeredMembers;
    }

    public ArrayList<Member> getJoiners() {
        ArrayList<Member> joiners = new ArrayList<>();
        for (Member member : this.answeredMembers.keySet()) {
            Pair<Boolean, Long> answer = this.answeredMembers.get(member);
            if (answer.getLeft()) {
                joiners.add(member);
            }
        }
        return joiners;
    }

    public boolean isCollectingTeam() {
        return isCollectingTeam;
    }

    public long getStartTime() {
        return startTime;
    }

    public long calculateStartTime(int time, int minute) {
        boolean addDay = time >= 24;
        time %= 24;

        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);
        cal.set(year, month, day, time, minute, 0);
        if (addDay) cal.add(Calendar.DAY_OF_MONTH, 1);
        return cal.getTimeInMillis();
    }

    public void setStartTime(int time, int minute) throws InvalidLolStartTimeException {
        // validate
        if (time < 0 || time > 47) {
            throw new InvalidLolStartTimeException("시간은 0부터 47까지만 가능합니다.");
        }
        if (minute < 0 || minute > 59) {
            throw new InvalidLolStartTimeException("분은 0부터 59까지만 가능합니다.");
        }

        long newStartTime = this.calculateStartTime(time, minute);
        if (newStartTime < System.currentTimeMillis()) {
            throw new InvalidLolStartTimeException("내전 시간은 현재 시간 이후로 설정해야 합니다.");
        } else if (newStartTime > System.currentTimeMillis() + 86400000) {
            throw new InvalidLolStartTimeException("내전 시간은 현재 시간으로부터 24시간 이내로 설정해야 합니다.");
        } else if (newStartTime == this.startTime) {
            throw new InvalidLolStartTimeException("이미 같은 시간으로 설정되어 있습니다.");
        }
        this.startTime = newStartTime;
    }
}
