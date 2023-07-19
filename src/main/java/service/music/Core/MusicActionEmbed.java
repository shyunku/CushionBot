package service.music.Core;

import Utilities.TextStyler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import service.music.object.YoutubeTrackInfo;

import java.util.ArrayList;

public class MusicActionEmbed {
    public MessageEmbed messageEmbed;
    public ActionRow musicController;
    public ActionRow musicTrackSelector;

    public MusicActionEmbed(MessageEmbed messageEmbed, ActionRow musicController, ActionRow musicTrackSelector) {
        this.messageEmbed = messageEmbed;
        this.musicController = musicController;
        this.musicTrackSelector = musicTrackSelector;
    }
}
