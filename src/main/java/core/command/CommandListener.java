package core.command;

import Utilities.ChannelPermissionInfo;
import core.command.CommandParser;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Arrays;
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

    public void addPermittedServer(String serverID){
        channelPermissionInfoHashMap.put(serverID, new ChannelPermissionInfo(serverID));
    }

    public void addPermittedTextChannel(TextChannel textChannel){
        String serverID = textChannel.getGuild().getId();
        ChannelPermissionInfo permissionInfo = channelPermissionInfoHashMap.get(serverID);
        if(permissionInfo == null){
            channelPermissionInfoHashMap.put(serverID, new ChannelPermissionInfo(serverID));
        }
        assert permissionInfo != null;
        permissionInfo.addWhiteList(textChannel);
    }

    public void addProhibitTextChannel(TextChannel textChannel){
        String serverID = textChannel.getGuild().getId();
        ChannelPermissionInfo permissionInfo = channelPermissionInfoHashMap.get(serverID);
        if(permissionInfo == null){
            channelPermissionInfoHashMap.put(serverID, new ChannelPermissionInfo(serverID));
        }
        assert permissionInfo != null;
        permissionInfo.addBlackList(textChannel);
    }

    public void printListenedMessage(){
        if(rb == null){
            System.out.println(title+"> " + "Error!");
        }else{
            System.out.println(title+"> " + rb.getAuthor().getName() + ": "
                    + commandParser.getRawString() + " [" + commandParser.getWholeString()+"]");
        }
    }

    public boolean isAllowedTextChannel(TextChannel textChannel){
        String serverID = textChannel.getGuild().getId();
        ChannelPermissionInfo permissionInfo = channelPermissionInfoHashMap.get(serverID);
        if(permissionInfo == null){
            return false;
        }
        return permissionInfo.isAllowed(textChannel);
    }

    public void parse(MessageReceivedEvent e){
        rb = e;
        commandParser.setThis(e.getMessage().getContentDisplay(), false);
    }

    public void sendback(String msg){
        rb.getTextChannel().sendMessage(msg).queue();
    }

    public boolean isListenable(MessageReceivedEvent e){
        if(!isAllowedTextChannel(e.getTextChannel())) return false;
        parse(e);
        return commandParser.getStartTag() == startTag;
    }

    public abstract void listen(MessageReceivedEvent e);
}
