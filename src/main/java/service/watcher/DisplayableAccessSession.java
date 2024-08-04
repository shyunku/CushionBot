package service.watcher;

public class DisplayableAccessSession {
    private String channelName;
    private long joinTime;
    private long leaveTime;

    public DisplayableAccessSession(String channelName, long joinTime, long leaveTime) {
        this.channelName = channelName;
        this.joinTime = joinTime;
        this.leaveTime = leaveTime;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
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
}
