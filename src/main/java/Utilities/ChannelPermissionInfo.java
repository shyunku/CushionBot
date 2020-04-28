package Utilities;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;

import java.util.ArrayList;

public class ChannelPermissionInfo {
    // shows allowed channels
    private String serverID;
    private ArrayList<String> whiteList = new ArrayList<>();    //Text channel ID bundle
    private ArrayList<String> blackList = new ArrayList<>();

    public ChannelPermissionInfo(String serverID){
        this.serverID = serverID;
    }

    public void addWhiteList(TextChannel textChannel){
        String newTextChannelID = textChannel.getId();
        blackList.remove(newTextChannelID);
        if(whiteList.contains(newTextChannelID))return;
        whiteList.add(textChannel.getId());
    }

    public void addBlackList(TextChannel textChannel){
        String newTextChannelID = textChannel.getId();
        whiteList.remove(newTextChannelID);
        if(blackList.contains(newTextChannelID))return;
        blackList.add(textChannel.getId());
    }

    public boolean isAllowed(TextChannel textChannel){
        Guild guild = textChannel.getGuild();
        String newTextChannelID = textChannel.getId();
        if(!serverID.equals(guild.getId())) return false;
        if(blackList.contains(newTextChannelID)) return false;
        if(whiteList.isEmpty()) return true;
        return whiteList.contains(newTextChannelID);
    }
}
