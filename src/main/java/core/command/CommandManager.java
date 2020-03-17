package core.command;

import Utilities.TextStyleManager;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import music.object.YoutubeTrackInfo;
import music.tools.TrackScheduler;
import music.tools.YoutubeCrawler;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.managers.AudioManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CommandManager {
    private TextChannel textChannel;
    private Guild guild;
    private User user;
    private final boolean SAFE_MODE = false;
    private CommandStatus mode = CommandStatus.NORMAL;
    private YoutubeCrawler youtubeCrawler = new YoutubeCrawler();

    //Memories
    private ArrayList<String> kickList = new ArrayList<>();
    private Message musicSelectMessage = null;
    private ArrayList<Member> whiteList = new ArrayList<>();
    private ArrayList<Role> whiteRoleList = new ArrayList<>();

    //FINAL
    private final int MAX_RETRIEVE_SIZE = 500;


    public void parseCommand(MessageReceivedEvent e){
        user = e.getAuthor();
        textChannel = e.getTextChannel();
        Message msg = e.getMessage();
        String text = msg.getContentDisplay();
        guild = e.getGuild();

        CommandParser commandParser = new CommandParser(text, false);
        ArrayList<String> segments = commandParser.getSegments();
        String sentence = commandParser.getIntegratedString();
        String keyword = commandParser.getKeyword();

        print(user.getName()+": "+text+ " [Channel "+textChannel.getId() + "/"+textChannel.getName()+"]");

        switch(mode){
            case NORMAL:
                switch(keyword){
                    case "alive": alive(); break;
                    case "j":
                    case "join": join(e); break;
                    case "l":
                    case "leave": leave(); break;
                    case "say": say(sentence, msg); break;
                    case "kick": kick(segments); break;
                    case "play": play(e, sentence); break;
                    case "clear": clear(e, segments.get(0)); break;
                    case "whitelist": whitelist(); break;
                }
                break;
            case ASK_KICK:
                switch(keyword){
                    case "yes":
                        for (String id : kickList) {
                            guild.kick(guild.getMemberById(id)).queue();
                        }
                    break;
                    default:
                        sendMessage("kicking canceled");
                        break;
                }
                mode = CommandStatus.NORMAL;
                break;
            case WAIT_SONG_PICK:
                switch(keyword){
                    case "p":
                    case "play":
                        try{
                            int songIndex = Integer.parseInt(segments.get(0));
                            if(songIndex<1||songIndex>5)throw new NumberFormatException();
                            play(e, songIndex);
                        }catch(NumberFormatException exception){
                            sendBoldMessage("잘못된 인자: 음악 실행 취소");
                        }
                        break;
                    default:
                        sendBoldMessage("음악 실행 취소");
                        break;
                }
                mode = CommandStatus.NORMAL;
                break;
        }
    }

    /* Commands Execution */
    private void alive(){
        sendMessage("I'm alive.");
    }

    private void join(MessageReceivedEvent e){
        VoiceChannel voiceChannel = e.getMember().getVoiceState().getChannel();
        if(voiceChannel == null){
            sendMessage("음악을 재생하시려면 음성채널에 먼저 입장해주세요!");
            return;
        }
        sendMessage("음성채널 "+textStyler.toBold(voiceChannel.getName())+"에 참여 중");
        AudioManager audioManager = guild.getAudioManager();
        if(audioManager.isAttemptingToConnect()){
            sendMessage("Bot is already attempting to join voice channel! Please try again.");
        }else{
            audioManager.openAudioConnection(voiceChannel);
        }
    }

    private void leave(){
        VoiceChannel connectedChannel = guild.getSelfMember().getVoiceState().getChannel();
        if(connectedChannel == null){
            sendMessage("나올 채널이 없습니다.");
        }else{
            guild.getAudioManager().closeAudioConnection();
        }
    }

    private void say(String str, Message msg){
        msg.delete().queue();
        sendMessage(str);
    }

    private void kick(ArrayList<String> segment){
        if(warningDangerousCommand())return;
        if(!hasWhitePermission())return;

        String roleName = segment.get(0).substring(1);
        Role role = guild.getRolesByName(roleName, false).get(0);
        sendMessage("Kick Mode => selected role: " + role.getName());
        sendMessage("Are you sure to kick below users? This can't be undone!");

        int kicked = 0;
        for (Member m : guild.getMembers()) {
            boolean isMatched = false;
            for (Role r : m.getRoles()) {
                if (r.getName().equals(roleName)) {
                    isMatched = true;
                    break;
                }
            }
            if (isMatched) {
                kickList.add(m.getId());
                sendMessage("kicking candidates ID[" + m.getId() + "]: " + m.getEffectiveName());
                kicked++;
            }
        }

        mode = CommandStatus.ASK_KICK;
        sendMessage("Selected users: " + kicked);
    }

    private void play(MessageReceivedEvent e, String searchKeyword){
        VoiceChannel voiceChannel = e.getMember().getVoiceState().getChannel();
        if(voiceChannel == null){
            sendMessage("음악을 재생하시려면 음성채널에 먼저 입장해주세요!");
            return;
        }
        ArrayList<YoutubeTrackInfo> trackInfoBundle = youtubeCrawler.getVideoCandidates(searchKeyword);
        StringBuilder res = new StringBuilder();
        int index = 0;
        for (YoutubeTrackInfo info : trackInfoBundle) {
            res.append(textStyler.toBold(++index + "")).append(". ").append(info.getTitle()).append("\n");
        }

        sendBoldMessage("명령어 " + textStyler.toBlock("$play 1-5") + "를 사용하여 재생하세요:");
        sendMessage(res.toString());

        mode = CommandStatus.WAIT_SONG_PICK;
    }

    private void play(MessageReceivedEvent e, int index){
        VoiceChannel currentVoiceChannel = guild.getSelfMember().getVoiceState().getChannel();
        if(currentVoiceChannel == null){
            join(e);
        }
    }

    private void clear(MessageReceivedEvent e, String amountStr){
        if(warningDangerousCommand())return;
        if(!hasWhitePermission())return;
        try{
            final int amount = Math.min(Integer.parseInt(amountStr), MAX_RETRIEVE_SIZE);

            MessageHistory messageHistory = textChannel.getHistory();
            messageHistory.retrievePast(amount).queue(messageList -> {
                textChannel.deleteMessages(messageList).queue();
                sendBoldMessage("최근 메시지 "+amount+"개가 "+textStyler.toBlock(e.getAuthor().getName())+"에 의해 삭제되었습니다.");
            });
        }catch(NumberFormatException exception){
            sendBoldMessage("잘못된 인자: clear 명령이 취소되었습니다.");
        }
    }

    private void whitelist(){
        boolean isEmpty = whiteList.isEmpty() && whiteRoleList.isEmpty();
        if(isEmpty){
            sendBoldMessage("현재 화이트리스트에 아무도 없습니다.");
            return;
        }

        if(!whiteList.isEmpty()){
            sendBoldMessage("화이트리스트 유저 "+whiteList.size()+"명");
            StringBuilder res = new StringBuilder();
            int index = 0;
            for(Member whites : whiteList){
                res.append(++index).append(". ").append(whites.getUser().getName()).append("\n");
            }
            sendWrappedMessage(res.toString());
        }

        if(!whiteRoleList.isEmpty()){
            sendBoldMessage("\n화이트리스트 역할 "+whiteRoleList.size()+"개");
            StringBuilder res = new StringBuilder();
            int index = 0;
            for(Role role : whiteRoleList){
                res.append(++index).append(". ").append(role.getName()).append("\n");
            }
            sendWrappedMessage(res.toString());
        }
    }


    /* Internal Util Functions */
    public void sendMessage(String msg){
        if(textChannel == null){
            print("Channel Not Allocated.");
        }else{
            textChannel.sendMessage(msg).queue();
        }
    }

    private void sendBoldMessage(String message){
        message = "**"+message + "**";
        textChannel.sendMessage(message).queue();
    }

    private void sendWrappedMessage(String message){
        message = "```"+message + "```";
        textChannel.sendMessage(message).queue();
    }

    private boolean warningDangerousCommand(){
        if(!SAFE_MODE)return false;
        sendBoldMessage("현재 봇이 안전모드에 있습니다. 위험한 명령어는 제한됩니다.");
        return true;
    }

    private boolean hasWhitePermission(){
        for(Member whites : whiteList){
            if(whites.getUser().getId().equals(user.getId()))
                return true;
        }
        List<Role> roles = guild.getMember(user).getRoles();
        for(Role role : roles){
            for(Role whiteRole : whiteRoleList){
                if(role.getId().equals(whiteRole.getId()))
                    return true;
            }
        }
        sendBoldMessage("화이트리스트에 있는 역할/유저들만 실행할 수 있는 명령어입니다.");
        return false;
    }

    public void privilege(String name){
        if(guild == null) {
            print("Channel Not Allocated.");
            return;
        }

        boolean found = false;

        for(Member member : guild.getMembers()){
            if(member.getUser().getName().equals(name)){
                whiteList.add(member);
                found = true;
                sendServerMessage("유저 "+name+"를 화이트리스트에 추가했습니다.");
                break;
            }
        }

        if(!found){
            for(Role role : guild.getRoles()){
                if(role.getName().equals(name)){
                    whiteRoleList.add(role);
                    found = true;
                    sendServerMessage("역할 "+role.getName()+"을 화이트리스트에 추가했습니다.");
                    break;
                }
            }
        }

        if(!found){
            print("그런 닉네임의 사용자/역할은 없습니다.");
        }
    }

    public void resetWhiteList(){
        whiteList.clear();
        whiteRoleList.clear();
        sendServerMessage("화이트리스트를 비웠습니다.");
    }

    private void sendServerMessage(String str){
        TextStyleManager styler = new TextStyleManager();
        String printer = styler.toBold("[Server] "+str);

        sendMessage(printer);
    }

    private void print(Object o){
        System.out.println(o);
    }

    private TextStyleManager textStyler = new TextStyleManager();
}