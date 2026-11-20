package service.discord;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.function.Consumer;

public class MessageEmbedProps {
    private static final Logger logger = LoggerFactory.getLogger(MessageEmbedProps.class);
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

    /** Edit the message; {@code onMissing} runs when the message no longer exists. */
    public void editMessageEmbed(Message message, Runnable onMissing) {
        if (message == null) return;
        message.editMessageEmbeds(this.messageEmbed).setComponents(actionRows).queue(null, failure -> {
            if (failure instanceof ErrorResponseException
                    && ((ErrorResponseException) failure).getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                onMissing.run();
            } else {
                logger.warn("Could not edit box message: {}", failure.getMessage());
            }
        });
    }

    public void sendMessageEmbedWithHook(TextChannel textChannel, Consumer<? super Message> success) {
        if (textChannel == null) return;
        textChannel.sendMessageEmbeds(this.messageEmbed).setComponents(actionRows).queue(success,
                failure -> logger.warn("Could not send box message: {}", failure.getMessage()));
    }
}
