package custom.IntegratedLOLclan.StringCorrection;

import Utilities.UtilFunctions;
import core.command.CommandListener;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;

public class ManiangListener extends CommandListener {
    private String[] subclanNameBundle = {"혼약", "결혼기념일", "마녀들", "천체", "빛의", "애절", "원한", "여우자리"};
    public ManiangListener(String title, char startTag) {
        super(title, startTag);
    }

    @Override
    public void listen(MessageReceivedEvent e) {
        if(!isListenable(e)) return;
        printListenedMessage();

        // 역할 요청 regex correction
        checkRoleRequestValidation();
    }

    private void checkRoleRequestValidation(){
        ArrayList<String> seg = commandParser.getSegments();
        String keyword = commandParser.getKeyword();
        if(seg.size() < 2)return;
        String requestSubclanName = seg.get(1);
        String subclanName;
        boolean pass = true;

        if(keyword.equals("역활")){
            pass = false;
        }
        if(!seg.get(0).equals("요청")){
            pass = false;
        }
        if(seg.size() > 2){
            List<String> strs = seg.subList(1, seg.size());
            requestSubclanName = UtilFunctions.concatStrArrWithoutBlank(strs);
        }
        double max_distance = 0;
        int min_dist_index = 0;
        for(int i=0;i<subclanNameBundle.length;i++){
            double dist = UtilFunctions.getStringDistance(requestSubclanName, subclanNameBundle[i]);
            if(dist > max_distance) {
                max_distance = dist;
                min_dist_index = i;
            }
        }
        subclanName = subclanNameBundle[min_dist_index];

        if(subclanName.equals(requestSubclanName) && pass) return;
        sendback("다음과 같이 입력바랍니다: **!역할 요청 "+ subclanName +"**");
    }
}
