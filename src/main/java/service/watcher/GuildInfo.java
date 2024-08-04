package service.watcher;

public class GuildInfo {
    public String id;
    public String name;
    public int memberCount;
    public String iconUrl;

    public GuildInfo(String id, String name, int memberCount, String iconUrl) {
        this.id = id;
        this.name = name;
        this.memberCount = memberCount;
        this.iconUrl = iconUrl;
    }
}
