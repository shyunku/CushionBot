package service.leagueoflegends.Core;

import Utilities.TextStyler;
import Utilities.Util;
import com.fasterxml.jackson.databind.ObjectMapper;
import exceptions.InvalidLolStartTimeException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.discord.ControlBox;
import service.discord.MessageEmbedProps;
import service.guild.core.GuildUtil;
import service.inmemory.RedisClient;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class LolBox implements ControlBox {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final Logger logger = LoggerFactory.getLogger(LolBox.class);
    private final Guild guild;
    private volatile Message lolBoxMessage;
    private volatile TextChannel lolChannel;
    private volatile boolean isCollectingTeam = false;
    private volatile long startTime = 0;

    /** user id -> answer; kept in Redis so a running scrim survives a restart */
    private final Map<String, Answer> answeredMembers = new ConcurrentHashMap<>();

    public static class Answer {
        public boolean join;
        public long at;

        public Answer() {
        }

        Answer(boolean join, long at) {
            this.join = join;
            this.at = at;
        }
    }

    public static class State {
        public boolean collecting;
        public long startTime;
        public Map<String, Answer> answers = new HashMap<>();
    }

    public LolBox(Guild guild, TextChannel lolChannel) {
        this.guild = guild;
        this.lolChannel = lolChannel;

        String chanKey = GuildUtil.lolChannelKey(guild.getId());
        if (RedisClient.has(chanKey)) {
            TextChannel channel = guild.getTextChannelById(RedisClient.get(chanKey));
            if (channel != null) this.lolChannel = channel;
        }

        String msgKey = GuildUtil.lolBoxMessageKey(guild.getId());
        if (this.lolChannel != null && RedisClient.has(msgKey)) {
            try {
                this.lolBoxMessage = this.lolChannel.retrieveMessageById(RedisClient.get(msgKey)).complete();
            } catch (RuntimeException e) {
                // deleted message, missing permission, ... -> a new box is posted below
                logger.warn("Could not load the LoL box message in {}: {}", guild.getName(), e.getMessage());
            }
        }

        loadState();
        this.updateEmbed();
    }

    private void loadState() {
        String key = GuildUtil.lolStateKey(guild.getId());
        if (!RedisClient.has(key)) return;
        try {
            State state = mapper.readValue(RedisClient.get(key), State.class);
            if (state.collecting && state.startTime > System.currentTimeMillis() - 6 * 60 * 60 * 1000L) {
                this.isCollectingTeam = true;
                this.startTime = state.startTime;
                this.answeredMembers.putAll(state.answers);
            }
        } catch (Exception e) {
            logger.warn("Could not restore LoL state for {}: {}", guild.getName(), e.getMessage());
        }
    }

    private void saveState() {
        String key = GuildUtil.lolStateKey(guild.getId());
        try {
            if (!isCollectingTeam) {
                RedisClient.del(key);
                return;
            }
            State state = new State();
            state.collecting = true;
            state.startTime = startTime;
            state.answers = new HashMap<>(answeredMembers);
            RedisClient.set(key, mapper.writeValueAsString(state));
        } catch (Exception e) {
            logger.warn("Could not save LoL state for {}: {}", guild.getName(), e.getMessage());
        }
    }

    /** Validates the time first; on an invalid time nothing (including existing votes) changes. */
    public synchronized void startCollectingTeam(int time, int minute) throws InvalidLolStartTimeException {
        long newStartTime = validateStartTime(time, minute);
        this.isCollectingTeam = true;
        this.answeredMembers.clear();
        this.startTime = newStartTime;
        saveState();
    }

    public void addMemberAnswer(String userId, boolean join) {
        if (!this.isCollectingTeam) return;
        this.answeredMembers.put(userId, new Answer(join, System.currentTimeMillis()));
        saveState();
    }

    public void removeMemberAnswer(String userId) {
        if (!this.isCollectingTeam) return;
        this.answeredMembers.remove(userId);
        saveState();
    }

    public synchronized void stopCollectingTeam() {
        this.isCollectingTeam = false;
        this.answeredMembers.clear();
        this.startTime = 0;
        saveState();
    }

    /** Lines joined with newlines, cut to fit an embed field (1024 characters) with a "외 N명" tail. */
    private static String fitField(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = (i == 0 ? "" : "\n") + lines.get(i);
            if (sb.length() + line.length() > MessageEmbed.VALUE_MAX_LENGTH - 16) {
                sb.append(String.format("\n외 %d명", lines.size() - i));
                break;
            }
            sb.append(line);
        }
        return sb.toString();
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
        embedBuilder.setColor(this.isCollectingTeam ? new Color(31, 255, 45) : new Color(88, 88, 88));

        Map<String, Answer> answers = new HashMap<>(this.answeredMembers);
        int notAnsweredCnt = Math.max(0, this.guild.getMemberCount() - answers.size());

        List<Map.Entry<String, Answer>> joiners = new ArrayList<>();
        List<Map.Entry<String, Answer>> notJoiners = new ArrayList<>();
        for (Map.Entry<String, Answer> entry : answers.entrySet()) {
            (entry.getValue().join ? joiners : notJoiners).add(entry);
        }
        Comparator<Map.Entry<String, Answer>> byTime = Comparator.comparingLong(entry -> entry.getValue().at);
        joiners.sort(byTime);
        notJoiners.sort(byTime);

        ActionRow controller = null;
        if (this.isCollectingTeam) {
            // the day label is built separately: putting it inside the date pattern broke on unknown words
            String startTimeStr = String.format("%s %s", Util.dirtyRelativeDay(this.startTime), Util.timeFormat(this.startTime, "(d일) a hh시 mm분"));
            embedBuilder.addField("시작 시간", TextStyler.Block(startTimeStr), false);

            embedBuilder.addField("참여자", TextStyler.Block(joiners.size() + " 명"), true);
            embedBuilder.addField("불참자", TextStyler.Block(notJoiners.size() + " 명"), true);
            embedBuilder.addField("무응답", TextStyler.Block(notAnsweredCnt + " 명"), true);

            List<String> joinerLines = new ArrayList<>();
            for (int i = 0; i < joiners.size(); i++) {
                Map.Entry<String, Answer> joiner = joiners.get(i);
                joinerLines.add(String.format("%d. <@%s> (%s)", i + 1, joiner.getKey(), Util.timeFormat(joiner.getValue().at, "a hh시 mm분")));
                // a divider after the first ten (one game's worth of players)
                if (i == 9 && joiners.size() > 10) joinerLines.add("---------------------------------");
            }
            if (!joinerLines.isEmpty()) embedBuilder.addField("⭕ 내전 참여자 목록", fitField(joinerLines), false);

            List<String> notJoinerLines = new ArrayList<>();
            for (int i = 0; i < notJoiners.size(); i++) {
                Map.Entry<String, Answer> notJoiner = notJoiners.get(i);
                notJoinerLines.add(String.format("%d. <@%s> (%s)", i + 1, notJoiner.getKey(), Util.timeFormat(notJoiner.getValue().at, "a hh시 mm분")));
            }
            if (!notJoinerLines.isEmpty()) embedBuilder.addField("❌ 내전 불참자 목록", fitField(notJoinerLines), false);

            controller = ActionRow.of(
                    Button.primary("lolJoin", String.format("참여 (%d)", joiners.size())),
                    Button.danger("lolNotJoin", String.format("불참 (%d)", notJoiners.size())),
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
        Message message = this.lolBoxMessage;
        if (message == null) {
            embed.sendMessageEmbedWithHook(this.lolChannel, this::setLolBoxMessage);
            return;
        }
        // the box was deleted (e.g. /clear): forget it and post a new one
        embed.editMessageEmbed(message, () -> {
            if (this.lolBoxMessage == message) {
                this.lolBoxMessage = null;
                updateEmbed();
            }
        });
    }

    /** Deletes the box and waits for Discord, so it is safe right before the process exits. */
    @Override
    public void clearEmbed() {
        Message message = this.lolBoxMessage;
        this.lolBoxMessage = null;
        RedisClient.del(GuildUtil.lolBoxMessageKey(guild.getId()));
        if (message == null) return;
        try {
            message.delete().complete();
        } catch (RuntimeException e) {
            logger.warn("Could not delete the LoL box in {}: {}", guild.getName(), e.getMessage());
        }
    }

    /** Point the box at a new channel; the old box is removed so its buttons do not linger. */
    public void setLolChannel(TextChannel lolChannel) {
        if (lolChannel == null) return;
        Message old = this.lolBoxMessage;
        if (old != null && !old.getChannel().getId().equals(lolChannel.getId())) {
            this.lolBoxMessage = null;
            old.delete().queue(null, failure -> {
            });
        }
        this.lolChannel = lolChannel;
        RedisClient.set(GuildUtil.lolChannelKey(guild.getId()), lolChannel.getId());
    }

    public void setLolBoxMessage(Message lolBoxMessage) {
        if (lolBoxMessage == null) return;
        Message old = this.lolBoxMessage;
        if (old != null && !old.getId().equals(lolBoxMessage.getId())) {
            old.delete().queue(null, failure -> {
            });
        }
        this.lolBoxMessage = lolBoxMessage;
        RedisClient.set(GuildUtil.lolBoxMessageKey(guild.getId()), lolBoxMessage.getId());
    }

    public TextChannel getLolChannel() {
        return lolChannel;
    }

    /** User ids of members who answered "참여", in answer order. */
    public List<String> getJoiners() {
        List<Map.Entry<String, Answer>> entries = new ArrayList<>(answeredMembers.entrySet());
        entries.sort(Comparator.comparingLong(entry -> entry.getValue().at));
        List<String> joiners = new ArrayList<>();
        for (Map.Entry<String, Answer> entry : entries) {
            if (entry.getValue().join) joiners.add(entry.getKey());
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

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);
        cal.set(year, month, day, time, minute, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (addDay) cal.add(Calendar.DAY_OF_MONTH, 1);
        return cal.getTimeInMillis();
    }

    private long validateStartTime(int time, int minute) throws InvalidLolStartTimeException {
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
        return newStartTime;
    }

    public synchronized void setStartTime(int time, int minute) throws InvalidLolStartTimeException {
        this.startTime = validateStartTime(time, minute);
        saveState();
    }
}
