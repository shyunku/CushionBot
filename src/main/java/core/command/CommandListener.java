package core.command;

import Utilities.ChannelPermissionInfo;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.HashMap;

public abstract class CommandListener {
    public String title;
    public char startTag;
    public HashMap<String, ChannelPermissionInfo> channelPermissionInfoHashMap = new HashMap<>();
    public CommandParser commandParser = new CommandParser();
    public MessageReceivedEvent rb = null;

    public CommandListener(String title, char startTag) {
        this.title = title;
        this.startTag = startTag;
    }

    public void addPermittedServer(String serverID) {
        channelPermissionInfoHashMap.put(serverID, new ChannelPermissionInfo(serverID));
    }

    public void addPermittedAudioChannel(AudioChannel audioChannel) {
        String serverID = audioChannel.getGuild().getId();
        ChannelPermissionInfo permissionInfo = channelPermissionInfoHashMap.get(serverID);
        if (permissionInfo == null) {
            channelPermissionInfoHashMap.put(serverID, new ChannelPermissionInfo(serverID));
        }
        assert permissionInfo != null;
        permissionInfo.addWhiteList(audioChannel);
    }

    public void addProhibitAudioChannel(AudioChannel audioChannel) {
        String serverID = audioChannel.getGuild().getId();
        ChannelPermissionInfo permissionInfo = channelPermissionInfoHashMap.get(serverID);
        if (permissionInfo == null) {
            channelPermissionInfoHashMap.put(serverID, new ChannelPermissionInfo(serverID));
        }
        assert permissionInfo != null;
        permissionInfo.addBlackList(audioChannel);
    }

    public void printListenedMessage() {
        if (rb == null) {
            System.out.println(title + "> " + "Error!");
        } else {
            System.out.println(title + "> " + rb.getAuthor().getName() + ": "
                    + commandParser.getRawString() + " [" + commandParser.getWholeString() + "]");
        }
    }

    public boolean isAllowedAudioChannel(AudioChannel audioChannel) {
        String serverID = audioChannel.getGuild().getId();
        ChannelPermissionInfo permissionInfo = channelPermissionInfoHashMap.get(serverID);
        if (permissionInfo == null) {
            return false;
        }
        return permissionInfo.isAllowed(audioChannel);
    }

    public void parse(MessageReceivedEvent e) {
        rb = e;
        commandParser.setThis(e.getMessage().getContentDisplay(), false);
    }

    public void sendback(String msg) {
        rb.getChannel().sendMessage(msg).queue();
    }

    public abstract void listen(MessageReceivedEvent e);
}
