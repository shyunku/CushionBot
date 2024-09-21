package dtos.teamgg;

public class SetSummonerLineFavorRequestDto {
    private String customGameConfigId;
    private String puuid;
    private int[] strength;

    public SetSummonerLineFavorRequestDto(String customGameConfigId, String puuid, int[] strength) {
        this.customGameConfigId = customGameConfigId;
        this.puuid = puuid;
        this.strength = strength;
    }

    public String getCustomGameConfigId() {
        return customGameConfigId;
    }

    public void setCustomGameConfigId(String customGameConfigId) {
        this.customGameConfigId = customGameConfigId;
    }

    public String getPuuid() {
        return puuid;
    }

    public void setPuuid(String puuid) {
        this.puuid = puuid;
    }

    public int[] getStrength() {
        return strength;
    }

    public void setStrength(int[] strength) {
        this.strength = strength;
    }
}
