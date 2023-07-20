package service.discord;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;

import java.util.ArrayList;

public class MessageEmbedPair {
    public MessageEmbed messageEmbed = null;
    public ArrayList<ActionRow> actionRows = new ArrayList<>();

    public MessageEmbedPair() {
    }
    public void setMessageEmbed(MessageEmbed messageEmbed) {
        this.messageEmbed = messageEmbed;
    }

    public void addActionRow(ActionRow actionRow) {
        this.actionRows.add(actionRow);
    }
}
