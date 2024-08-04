package service.watcher;

import com.fasterxml.jackson.annotation.JsonIgnore;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

public class AccessSession {
    private String userId;
    private String channelId;
    private long joinTime;
    private long leaveTime;

    public AccessSession() {
    }

    public AccessSession(String userId, String channelId, long joinTime) {
        this.userId = userId;
        this.channelId = channelId;
        this.joinTime = joinTime;
    }

    public AccessSession(String userId, String channelId, long joinTime, long leaveTime) {
        this.userId = userId;
        this.channelId = channelId;
        this.joinTime = joinTime;
        this.leaveTime = leaveTime;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public long getJoinTime() {
        return joinTime;
    }

    public void setJoinTime(long joinTime) {
        this.joinTime = joinTime;
    }

    public long getLeaveTime() {
        return leaveTime;
    }

    public void setLeaveTime(long leaveTime) {
        this.leaveTime = leaveTime;
    }

    @JsonIgnore
    public long getDuration() {
        if (joinTime == 0) return 0;
        if (leaveTime == 0) return System.currentTimeMillis() - joinTime;
        return leaveTime - joinTime;
    }

    @JsonIgnore
    public boolean isComplete() {
        return joinTime != 0 && leaveTime != 0;
    }

    public DisplayableAccessSession toDisplayable(JDA jda, String guildId) {
        Guild guild = jda.getGuildById(guildId);
        VoiceChannel channel = guild.getVoiceChannelById(channelId);
        String channelName = channel == null ? "Unknown" : channel.getName();
        return new DisplayableAccessSession(channelName, joinTime, leaveTime);
    }
}
