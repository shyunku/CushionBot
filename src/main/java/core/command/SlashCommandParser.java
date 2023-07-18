package core.command;

import core.InternalEventListener;
import core.Service;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.HashMap;

public class SlashCommandParser {
    private final Logger logger = LoggerFactory.getLogger(SlashCommandParser.class);

    public SlashCommandParser() {

    }

    public void test(SlashCommandInteractionEvent e) {
        e.reply("test response").queue();
    }

    public void music(SlashCommandInteractionEvent e) {
        try {
            String guildId = e.getGuild().getId();
            String textChannelId = e.getTextChannel().getId();
            Service.guildMusicChannelMap.put(guildId, textChannelId);
            e.reply(String.format(
                    "\"%s\" 채널이 \"%s\" 서버의 음악 채널로 지정되었습니다!",
                    e.getTextChannel().getName(), e.getGuild().getName())).queue();
        } catch (Exception err) {
            err.printStackTrace();
        }
    }
}
