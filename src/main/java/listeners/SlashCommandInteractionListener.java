package listeners;

import core.command.SlashCommandParser;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class SlashCommandInteractionListener extends ListenerAdapter {
    private final SlashCommandParser slashCommandParser = new SlashCommandParser();

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
        String eventName = e.getName();
        switch (eventName) {
            case "test":
                slashCommandParser.test(e);
                break;
            case "clear":
                slashCommandParser.clear(e);
                break;
            case "점검완료":
                slashCommandParser.finishMaintenance(e);
                break;
            case "서버랭킹":
                slashCommandParser.serverRanking(e);
                break;
            case "내랭킹":
                slashCommandParser.myRanking(e);
                break;
            case "음악채널":
                slashCommandParser.music(e);
                break;
            case "음악셔플":
                slashCommandParser.musicShuffle(e);
                break;
            case "음악볼륨":
                slashCommandParser.musicVolume(e);
                break;
            case "내전채널":
                slashCommandParser.lol5vs5(e);
                break;
            case "내전모으기":
                slashCommandParser.lol5vs5StartOrStop(e, true);
                break;
            case "내전시간변경":
                slashCommandParser.lol5vs5ChangeStartTime(e);
                break;
            case "내전정보":
                slashCommandParser.lol5vs5PrintInfo(e);
                break;
            case "내전호출":
                slashCommandParser.lol5vs5Call(e);
                break;
            case "내전종료":
            case "내전취소":
                slashCommandParser.lol5vs5StartOrStop(e, false);
                break;
            case "구인채널생성":
                slashCommandParser.recruitChannel(e);
                break;
            case "구인":
                slashCommandParser.recruit(e);
                break;
            case "구인취소":
                slashCommandParser.recruitCancel(e);
                break;
            case "구인수정":
                slashCommandParser.recruitModify(e);
                break;
            case "구인광고":
                slashCommandParser.recruitAd(e);
                break;
            case "팀지지등록":
                slashCommandParser.teamggRegister(e);
                break;
            case "팀지지라인":
                slashCommandParser.teamggLineFavor(e);
                break;
        }
    }
}
