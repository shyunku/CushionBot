package service.watcher;

public class UserInfo {
    public String id;
    public String nickname;
    public String effectiveName;
    public String avatarUrl;

    public UserInfo(String id, String nickname, String effectiveName, String avatarUrl) {
        this.id = id;
        this.nickname = nickname;
        this.effectiveName = effectiveName;
        this.avatarUrl = avatarUrl;
    }
}
