package dtos.teamgg;

public class SetSummonerLineFavorRequestDto {
    private String userId;
    private int[] strengths;

    public SetSummonerLineFavorRequestDto(String userId, int[] strengths) {
        this.userId = userId;
        this.strengths = strengths;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int[] getStrengths() {
        return strengths;
    }

    public void setStrengths(int[] strengths) {
        this.strengths = strengths;
    }
}
