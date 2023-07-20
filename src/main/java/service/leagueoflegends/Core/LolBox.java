package service.leagueoflegends.Core;

import Utilities.TextStyler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.discord.MessageEmbedPair;
import service.guild.core.GuildUtil;
import service.inmemory.RedisClient;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class LolBox {
    private final Logger logger = LoggerFactory.getLogger(LolBox.class);
    private Guild guild;
    private Message lolBoxMessage;
    private TextChannel lolChannel;
    private boolean isCollectingTeam = false;

    private HashMap<Member, Pair<Boolean, Long>> answeredMembers = new HashMap<>();

    public LolBox(Guild guild, TextChannel lolChannel) {
        this.guild = guild;
        this.lolChannel = lolChannel;

        String chanKey = GuildUtil.lolChannelKey(guild.getId());
        if(RedisClient.has(chanKey)) {
            String chanId = RedisClient.get(chanKey);
            TextChannel channel = guild.getTextChannelById(chanId);
            if(channel != null) {
                this.lolChannel = channel;
            }
        }

        String msgKey = GuildUtil.lolBoxMessageKey(guild.getId());
        if(RedisClient.has(msgKey)) {
            String msgId = RedisClient.get(msgKey);
            Message message = this.lolChannel.retrieveMessageById(msgId).complete();
            if(message != null) {
                this.lolBoxMessage = message;
            }
        }
    }

    public void startCollectingTeam() {
        this.isCollectingTeam = true;
        this.answeredMembers.clear();
    }

    public void addMemberAnswer(Member member, boolean join) {
        if(!this.isCollectingTeam) return;
        this.answeredMembers.put(member, Pair.of(join, System.currentTimeMillis()));
    }

    public void removeMemberAnswer(Member member) {
        if(!this.isCollectingTeam) return;
        this.answeredMembers.remove(member);
    }

    public void stopCollectingTeam() {
        this.isCollectingTeam = false;
        this.answeredMembers.clear();
    }

    public MessageEmbedPair getEmbed() {
        MessageEmbedPair embedPair = new MessageEmbedPair();
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("LOL 내전 관리자");
        embedBuilder.setDescription("내전 참여/불참 여부 및 구성을 담당합니다.");
        embedBuilder.addField("내전 상태",
                TextStyler.Block(this.isCollectingTeam ? "인원 모집 중" : "현재는 내전 일정이 없습니다."), false);

        if(this.isCollectingTeam) {
            embedBuilder.setColor(new Color(31, 255, 45));
        } else {
            embedBuilder.setColor(new Color(88, 88, 88));
        }

        int totalMembers = this.guild.getMemberCount();
        int notAnsweredCnt = totalMembers - this.answeredMembers.size();

        int joinerCount = 0;
        for(Member member : this.answeredMembers.keySet()) {
            Pair<Boolean, Long> answer = this.answeredMembers.get(member);
            if(answer.getLeft()) {
                joinerCount++;
            }
        }
        int notJoiners = this.answeredMembers.size() - joinerCount;

        ActionRow controller = null;
        if(this.isCollectingTeam) {
            embedBuilder.addField("참여자", TextStyler.Block(joinerCount + " 명"), true);
            embedBuilder.addField("불참자", TextStyler.Block(notJoiners + " 명"), true);
            embedBuilder.addField("무응답", TextStyler.Block(notAnsweredCnt + " 명"), true);

            ArrayList<Pair<Member, Long>> joiners = new ArrayList<>();
            for(Member member : this.answeredMembers.keySet()) {
                Pair<Boolean, Long> answer = this.answeredMembers.get(member);
                if(answer.getLeft()) {
                    joiners.add(Pair.of(member, answer.getRight()));
                }
            }

            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            StringBuilder sb = new StringBuilder();
            joiners.sort((a, b) -> (int) (a.getRight() - b.getRight()));
            for(int i = 0; i < joiners.size(); i++) {
                Pair<Member, Long> joiner = joiners.get(i);
                String time = sdf.format(new Date(joiner.getRight()));
                sb.append(String.format("%d. %s (%s)", i + 1, String.format("<@%s>", joiner.getLeft().getId()), time));
                if(i != joiners.size() - 1) {
                    sb.append("\n");
                }
                if (i == 9) {
                    sb.append("---------------------------------\n");
                }
            }

            embedBuilder.addField("내전 참여 인원 목록", sb.toString(), false);

            controller = ActionRow.of(
                Button.primary("lolJoin", String.format("참여 (%d)", joinerCount)),
                Button.danger("lolNotJoin", String.format("불참 (%d)", notJoiners)),
                Button.secondary("lolDontKnow", "무응답")
            );
        }

        embedPair.setMessageEmbed(embedBuilder.build());
        if(controller != null) {
            embedPair.addActionRow(controller);
        }
        return embedPair;
    }

    public void updateEmbed() {
        MessageEmbedPair pair = this.getEmbed();
        if(this.lolBoxMessage == null) {
            this.lolChannel.sendMessageEmbeds(pair.messageEmbed).setActionRows(pair.actionRows).queue(message -> {
                setLolBoxMessage(message);
                this.updateNonNullLolActionEmbed(pair);
            });
            return;
        }
        this.updateNonNullLolActionEmbed(pair);
    }

    private void updateNonNullLolActionEmbed(MessageEmbedPair pair) {
        this.lolBoxMessage.editMessageEmbeds(pair.messageEmbed).setActionRows(pair.actionRows).queue();
    }

    public void setLolChannel(TextChannel lolChannel) {
        if(lolChannel == null) return;
        this.lolChannel = lolChannel;
        RedisClient.set(GuildUtil.lolChannelKey(guild.getId()), lolChannel.getId());
    }

    public void setLolBoxMessage(Message lolBoxMessage) {
        if(lolBoxMessage == null) return;
        this.lolBoxMessage = lolBoxMessage;
        RedisClient.set(GuildUtil.lolBoxMessageKey(guild.getId()), lolBoxMessage.getId());
    }

    public TextChannel getLolChannel() {
        return lolChannel;
    }

    public HashMap<Member, Pair<Boolean, Long>> getAnsweredMembers() {
        return answeredMembers;
    }
}
