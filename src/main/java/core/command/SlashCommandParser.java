package core.command;

import core.InternalEventListener;
import core.Service;
import core.Version;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.music.Core.MusicActionEmbed;
import service.music.Core.MusicBox;

import java.awt.*;
import java.util.HashMap;

public class SlashCommandParser {
    private final Logger logger = LoggerFactory.getLogger(SlashCommandParser.class);

    public SlashCommandParser() {

    }

    public void test(SlashCommandInteractionEvent e) {
        e.reply(String.format("Test response: CushionBot v%s", Version.CURRENT)).queue();
    }

    public void music(SlashCommandInteractionEvent e) {
        try {
            Guild guild = e.getGuild();
            if(guild == null) {
                e.reply("This command can only be used in a guild.").queue();
                return;
            }
            String guildId = guild.getId();
            TextChannel textChannel = e.getTextChannel();
            Service.addGuildManagerIfNotExists(guild);
            MusicBox musicBox = Service.GetMusicBoxByGuildId(guildId);
            musicBox.setMusicChannel(textChannel);

            MessageEmbed embed = musicBox.getInitialSettingEmbed();
            e.replyEmbeds(embed).queue(interactionHook -> {
                interactionHook.retrieveOriginal().queue(musicBox::setMusicBoxMessage);
            });
        } catch (Exception err) {
            err.printStackTrace();
        }
    }
}
