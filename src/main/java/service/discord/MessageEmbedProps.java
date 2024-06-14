package service.discord;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;

import java.util.ArrayList;
import java.util.function.Consumer;

public class MessageEmbedProps {
    public MessageEmbed messageEmbed = null;
    public ArrayList<ActionRow> actionRows = new ArrayList<>();

    public MessageEmbedProps() {
    }

    public void setMessageEmbed(MessageEmbed messageEmbed) {
        this.messageEmbed = messageEmbed;
    }

    public void addActionRow(ActionRow actionRow) {
        this.actionRows.add(actionRow);
    }

    public void editMessageEmbed(Message message) {
        if (message == null) return;
        message.editMessageEmbeds(this.messageEmbed).setComponents(actionRows).queue();
    }

    public void sendMessageEmbedWithHook(TextChannel textChannel, Consumer<? super Message> success) {
        if (textChannel == null) return;
        textChannel.sendMessageEmbeds(this.messageEmbed).setComponents(actionRows).queue(success);
    }

    private ArrayList<ItemComponent> getActionRowComponents() {
        ArrayList<ItemComponent> itemComponents = new ArrayList<>();
        for (ActionRow actionRow : actionRows) {
            itemComponents.addAll(actionRow.getComponents());
        }
        return itemComponents;
    }
}
