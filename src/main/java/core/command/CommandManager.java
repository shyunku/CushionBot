package core.command;

import Utilities.TextStyleManager;
import Utilities.TokenManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import music.Core.MusicStreamSystem;
import music.object.MusicPlayMode;
import music.object.YoutubeTrackInfo;
import music.tools.YoutubeCrawler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.managers.AudioManager;

import java.awt.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CommandManager {
    private final char START_TAG = '$';
    private final boolean SAFE_MODE = false;
    private final boolean RESTRICT_MODE = false;
    private final String BOT_CLIENT_ID = new TokenManager().getBotClientID();

    /* Message Properties */
    private TextChannel textChannel;
    private Guild guild;
    private User user;
    private Message currentMessage;

    /* Utility Tools */
    private CommandStatus mode = CommandStatus.NORMAL;
    private YoutubeCrawler youtubeCrawler = new YoutubeCrawler();
    private MusicStreamSystem musicStreamSystem = new MusicStreamSystem();
    private AudioPlayerManager audioPlayerManager;

    /* Memories */
    private ArrayList<String> kickList = new ArrayList<>();
    private Message previousBotMessage = null;
    private ArrayList<Member> whiteList = new ArrayList<>();
    private ArrayList<Role> whiteRoleList = new ArrayList<>();
    private ArrayList<YoutubeTrackInfo> trackCandidates = new ArrayList<>();
    private AudioManager currentAudioManager = null;
    private Message previousCommandUser = null;


    /* Constants */
    private final int MAX_RETRIEVE_SIZE = 500;

    public CommandManager() {
        audioPlayerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(audioPlayerManager);
        AudioSourceManagers.registerLocalSource(audioPlayerManager);
    }

    public void parseCommand(MessageReceivedEvent e){
        user = e.getAuthor();
        textChannel = e.getTextChannel();
        currentMessage = e.getMessage();
        String text = currentMessage.getContentDisplay();
        guild = e.getGuild();

        if(user.isBot()){
            if(user.getId().equals(BOT_CLIENT_ID)){
                previousBotMessage = currentMessage;
                print("BOT: "+previousBotMessage);
            }
            return;
        }
        if(text.length()==0)return;
        if(START_TAG != text.charAt(0))return;

        CommandParser commandParser = new CommandParser(text, false);
        ArrayList<String> segments = commandParser.getSegments();
        String sentence = commandParser.getIntegratedString();
        String keyword = commandParser.getKeyword();

        print(user.getName()+"("+user.getId()+"): "+text+ " [Channel "+textChannel.getId() + "/"+textChannel.getName()+"]");

        switch(mode){
            case NORMAL:
                switch(keyword){
                    case "alive": alive(); break;
                    case "j":
                    case "join": join(e); break;
                    case "l":
                    case "leave": leave(); break;
                    case "say": say(sentence, currentMessage); break;
                    case "kick": kick(segments); break;
                    case "p":
                    case "play": play(e, sentence); break;
                    case "clear":
                        if(segments.isEmpty()){
                            sendMessage("clear 명령어 뒤에 삭제하실 최근 메시지의 수를 입력해주세요!");
                            break;
                        }else{
                            clear(e, segments.get(0)); break;
                        }
                    case "whitelist": whitelist(); break;
                    case "repeat": repeatTrackList(segments); break;
                    case "queue": musicQueue(); break;
                    case "command": printCommand(); break;
                    case "mclear": musicClear(); break;
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
//        sendMessage("음성채널 "+textStyler.toBold(voiceChannel.getName())+"에 참여 중");
        currentAudioManager = guild.getAudioManager();
        if(currentAudioManager.isAttemptingToConnect()){
            sendMessage("Bot is already attempting to join voice channel! Please try again.");
        }else{
            currentAudioManager.openAudioConnection(voiceChannel);
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
        currentMessage.delete().queue();
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
        trackCandidates = youtubeCrawler.getVideoCandidates(searchKeyword);
        StringBuilder res = new StringBuilder();
        res.append(textStyler.toBold("명령어 " + textStyler.toBlock("$play 1-5") + "를 사용하여 재생하세요:")).append("\n");
        int index = 0;
        for (YoutubeTrackInfo info : trackCandidates) {
            res.append(textStyler.toBold(++index + "")).append(". ").append(info.getTitle()).append("\n");
        }

//        deleteReceivedCurrentMessage();
        sendMessage(res.toString());

        mode = CommandStatus.WAIT_SONG_PICK;
    }

    private void play(MessageReceivedEvent e, int index){
        VoiceChannel currentVoiceChannel = guild.getSelfMember().getVoiceState().getChannel();
        if(currentVoiceChannel == null){
            join(e);
        }
        YoutubeTrackInfo selectedTrackInfo = trackCandidates.get(index-1);
        trackCandidates.clear();

        deleteReceivedCurrentMessage();
        deleteSentPreviousMessage();

        musicStreamSystem.registerMusicStreamer(currentAudioManager, audioPlayerManager, textChannel);
        musicStreamSystem.addTrackToQueue(textChannel, audioPlayerManager, selectedTrackInfo);
    }

    private void clear(MessageReceivedEvent e, String amountStr){
        if(warningDangerousCommand())return;
        if(!hasWhitePermission())return;
        try{
            final int amount = Math.min(Integer.parseInt(amountStr), MAX_RETRIEVE_SIZE);

            MessageHistory messageHistory = textChannel.getHistory();
            messageHistory.retrievePast(amount).queue(messageList -> {
                textChannel.deleteMessages(messageList).queue();
                String boldStr = textStyler.toBold("최근 메시지 "+amount+"개가 "+textStyler.toBlock(e.getAuthor().getName())+"에 의해 삭제되었습니다.");

                textChannel.sendMessage(boldStr).queue(res -> {
                    deleteSentPreviousMessage(3);
                });
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

    private void repeatTrackList(ArrayList<String> segments){
        MusicPlayMode musicPlayMode = MusicPlayMode.NORMAL;
        if(segments.isEmpty()){
            musicPlayMode = MusicPlayMode.REPEAT_SINGLE;
            sendBoldMessage("재생 모드가 현재 트랙 반복으로 변경되었습니다.");
        }else{
            String segment = segments.get(0);
            switch(segment){
                case "all":
                    musicPlayMode = MusicPlayMode.REPEAT_ALL;
                    sendBoldMessage("재생 모드가 전체 반복으로 변경되었습니다.");
                    break;
                case "off":
                    sendBoldMessage("재생 모드가 기본으로 변경되었습니다.");
                    //NORMAL 모드로 정상화
                    break;
                default:
                    //기타 세그먼트는 무시
                    return;
            }
        }
        musicStreamSystem.repeatTrackToQueue(textChannel, musicPlayMode);
    }

    private void musicQueue(){
        ArrayList<YoutubeTrackInfo> trackInfos = musicStreamSystem.getMusicStreamer(textChannel).getTrackScheduler().getTrackDataList();
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Current Track List");
        embedBuilder.setDescription("현재 "+trackInfos.size()+"개의 트랙이 있습니다.");
        embedBuilder.setColor(new Color(0, 255, 187));
        for(YoutubeTrackInfo trackInfo : trackInfos){
            embedBuilder.addField(trackInfo.getTitle(), trackInfo.getChannelTitle(), true);
        }

        textChannel.sendMessage(embedBuilder.build()).queue();
    }

    private void printCommand(){
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Cushion Bot Command List");
        embedBuilder.setColor(new Color(0, 255, 187));
        embedBuilder.addField(START_TAG + "command", "Cushion 봇 명령어 목록을 출력합니다.", false);
        embedBuilder.addField(START_TAG + "alive", "현재 Cushion이 이용가능하면 메시지를 보냅니다.", false);
        embedBuilder.addField(START_TAG + "join(j)", "Cushion을 현재 음성 채널에 참가시킵니다.", false);

        textChannel.sendMessage(embedBuilder.build()).queue();
    }

    private void musicClear(){
        musicStreamSystem.clearTracksOfQueue(textChannel);
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
        if(!RESTRICT_MODE)return true;

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

    private void deleteReceivedCurrentMessage(){
        if(currentMessage == null) return;
        currentMessage.delete().queue();
        currentMessage = null;
    }

    private void deleteSentPreviousMessage(){
        if(previousBotMessage == null) return;
        previousBotMessage.delete().queue();
        previousBotMessage = null;
    }

    private void deleteSentPreviousMessage(long delaySec){
        if(previousBotMessage == null) return;
        previousBotMessage.delete().queueAfter(delaySec, TimeUnit.SECONDS);
        previousBotMessage = null;
    }

    public void privilege(String name){
        if(guild == null) {
            print("Channel Not Allocated.");
            return;
        }

        boolean found = false;

        for(Member member : guild.getMembers()){
            if(member.getNickname().equals(name)){
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