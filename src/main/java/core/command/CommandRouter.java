package core.command;

import Utilities.TextStyleManager;
import Utilities.TokenManager;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import exceptions.CommandArgumentsOutOfBoundException;
import exceptions.CommandMusicPlayIndexOutOfRangeException;
import music.Core.MusicStreamSystem;
import music.object.MusicPlayMode;
import music.object.YoutubeTrackInfo;
import music.tools.YoutubeCrawler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.managers.AudioManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CommandRouter {
    private final Logger logger = LoggerFactory.getLogger(CommandRouter.class);
    private final char START_TAG = '$';
    private final boolean SAFE_MODE = false;
    private final boolean RESTRICT_MODE = false;

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
    private CommandParser commandParser = new CommandParser();

    /* Memories */
    private ArrayList<String> kickList = new ArrayList<>();
    private ArrayList<Member> whiteList = new ArrayList<>();
    private ArrayList<Role> whiteRoleList = new ArrayList<>();
    private ArrayList<YoutubeTrackInfo> trackCandidates = new ArrayList<>();
    private AudioManager currentAudioManager = null;

    private Message previousBotMessage = null;
    private Message currentUserCommand = null;
    private Message previousUserCommand = null;

    /* Constants */
    private final int MAX_RETRIEVE_SIZE = 500;

    public CommandRouter() {
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
        if(text.length()==0)return;

        if(user.isBot()){
            if(user.getId().equals(TokenManager.BOT_CLIENT_ID)){
                logger.info(String.format("BOT Message --- %s", currentMessage));
                previousBotMessage = currentMessage;
            }
            return;
        }

        if(START_TAG == text.charAt(0)){
            currentUserCommand = currentMessage;
            logger.info(String.format("User Command {%s} --- [%s][%s] %s: %s",
                    mode, guild.getName(), textChannel.getName(), user.getName(), text));
        }

        commandParser.setThis(text, false);
        ArrayList<String> segments = commandParser.getSegments();
        String sentence = commandParser.getIntegratedString();
        String keyword = commandParser.getKeyword();

        musicStreamSystem.registerMusicStreamer(currentAudioManager, audioPlayerManager, textChannel);

        switch(mode){
            case NORMAL:
                switch(keyword){
                    case "alive": alive(); break;
                    case "j":
                    case "join": join(e); break;
                    case "l":
                    case "leave": leave(); break;
                    case "say": say(sentence, currentMessage); break;
//                    case "kick": kick(segments); break;
                    case "p":
                    case "play": play(e, sentence); break;
                    case "qp":
                    case "quickplay": quickPlay(e, sentence); break;
                    case "clear":
                        if(segments.isEmpty()){
                            sendMessage("clear 명령어 뒤에 삭제하실 최근 메시지의 수를 입력해주세요!");
                            break;
                        }else{
                            clear(e, segments.get(0)); break;
                        }
//                    case "whitelist": whitelist(); break;
                    case "repeat": repeatTrackList(segments); break;
                    case "q":
                    case "queue": musicQueue(); break;
                    case "help":
                    case "command": printCommand(); break;
                    case "mclear": musicClear(); break;
                    case "s":
                    case "skip": musicSkip(); break;
                }
                break;
            case ASK_KICK:
                switch(keyword){
                    case "yes":
                        for (String id : kickList) {
//                            guild.kick(guild.getMemberById(id)).queue();
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
                            if(segments.isEmpty()) throw new CommandArgumentsOutOfBoundException(segments.size(), 0);
                            int songIndex = Integer.parseInt(segments.get(0));
                            if(songIndex<1 || songIndex>5) throw new CommandMusicPlayIndexOutOfRangeException();
                            playByIndex(e, songIndex);
                        } catch (NumberFormatException exc) {
                            play(e, sentence);
                        } catch (CommandArgumentsOutOfBoundException | CommandMusicPlayIndexOutOfRangeException exc) {
                            sendBoldMessage("잘못된 인자: 음악 실행 취소");
                        }
                        break;
                    default:
                        sendBoldMessage("음악 실행 취소");
                        break;
                }
                break;
        }

        previousUserCommand = currentMessage;
    }

    /* Commands Execution */
    private void alive(){
        sendMessage("I'm alive.");
    }

    private void join(MessageReceivedEvent e){
        AudioChannel voiceChannel = e.getMember().getVoiceState().getChannel();
        if(voiceChannel == null){
            sendMessage("음악을 재생하시려면 음성채널에 먼저 입장해주세요!");
            return;
        }
        currentAudioManager = guild.getAudioManager();
        if(currentAudioManager.getConnectionStatus() == ConnectionStatus.CONNECTING_ATTEMPTING_UDP_DISCOVERY){
            sendMessage("Bot is already attempting to join voice channel! Please try again.");
        }else{
            currentAudioManager.openAudioConnection(voiceChannel);
        }
    }

    private void leave(){
        AudioChannel connectedChannel = guild.getSelfMember().getVoiceState().getChannel();
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
        AudioChannel voiceChannel = e.getMember().getVoiceState().getChannel();

        if(voiceChannel == null){
            sendMessage("음악을 재생하시려면 음성채널에 먼저 입장해주세요!");
            return;
        }

        if(searchKeyword.contains("www.youtube.com")){
            musicStreamSystem.addTrackListToQueue(textChannel, audioPlayerManager, searchKeyword);
            return;
        }

        trackCandidates = youtubeCrawler.getVideoCandidates(searchKeyword);
        if(trackCandidates.isEmpty()){
            sendMessage("해당 검색어의 결과가 없습니다.");
            return;
        }
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

    private void playByIndex(MessageReceivedEvent e, int index){
        AudioChannel currentVoiceChannel = guild.getSelfMember().getVoiceState().getChannel();
        if(currentVoiceChannel == null){
            join(e);
        }
        YoutubeTrackInfo selectedTrackInfo = trackCandidates.get(index-1);
        trackCandidates.clear();

        deleteCurrentUserCommand();
        deleteSentPreviousMessage();

        musicStreamSystem.addTrackToQueue(textChannel, audioPlayerManager, selectedTrackInfo);
        mode = CommandStatus.NORMAL;
    }

    private void quickPlay(MessageReceivedEvent e, String searchKeyword){
        AudioChannel currentVoiceChannel = guild.getSelfMember().getVoiceState().getChannel();
        if(currentVoiceChannel == null){
            join(e);
        }
        if(trackCandidates.isEmpty()){
            sendMessage("해당 검색어의 결과가 없습니다.");
            return;
        }
        YoutubeTrackInfo selectedTrackInfo = youtubeCrawler.getVideoCandidates(searchKeyword).get(0);
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
        embedBuilder.setDescription("현재 "+trackInfos.size()+"개의 트랙이 있습니다. - 재생모드: "
                +textStyler.toBold(musicStreamSystem.getPlayModeDescription(textChannel)));
        embedBuilder.setColor(new Color(0, 255, 187));
        ArrayList<StringBuilder> stringBuilders = new ArrayList<>();
        StringBuilder trackInfoList = new StringBuilder();

        int index = 1;
        for(YoutubeTrackInfo trackInfo : trackInfos){
            int nextTotalStringLen = trackInfoList.toString().length() + 7 + trackInfo.getTitle().length();
            if(nextTotalStringLen >= 1024){
                stringBuilders.add(trackInfoList);
                trackInfoList = new StringBuilder();
            }
            trackInfoList.append(index++).append(". ").append(trackInfo.getTitle());
            if(index <= trackInfos.size())
                trackInfoList.append("\n");
        }
        stringBuilders.add(trackInfoList);

        for(int i=0;i<stringBuilders.size();i++){
            @Nullable String tmpTitle = "트랙리스트 페이지 "+(i+1)+"/"+stringBuilders.size();
            embedBuilder.addField(textStyler.toBold(tmpTitle), stringBuilders.get(i).toString(), false);
        }

        textChannel.sendMessageEmbeds(embedBuilder.build()).queue();
    }

    private void printCommand(){
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("쿠션 봇 명령어 목록");
        embedBuilder.setColor(new Color(0, 255, 187));
        embedBuilder.addField(START_TAG + "help|command", "Cushion 봇 명령어 목록을 출력합니다.", false);
        embedBuilder.addField(START_TAG + "alive", "현재 Cushion이 이용가능하면 메시지를 보냅니다.", false);
        embedBuilder.addField(START_TAG + "join(j)", "Cushion을 현재 음성 채널에 참가시킵니다.", false);
        embedBuilder.addField(START_TAG + "leave(l)", "Cushion을 현재 참가중인 음성 채널에서 퇴장시킵니다.", false);
        embedBuilder.addField(START_TAG + "play(p) <음악 검색 키워드>", "(유튜브 검색 기반) 음악을 재생합니다.", false);
        embedBuilder.addField(START_TAG + "quickplay(qp) <음악 검색 키워드>", "검색된 음악 중 가장 관련성이 높은 음악을 즉시 재생합니다.", false);
        embedBuilder.addField(START_TAG + "clear <삭제할 메시지 수>", "현재 텍스트 채널에서 최근 작성된 메시지를 삭제합니다.", false);
        embedBuilder.addField(START_TAG + "repeat <all|off>", "재생 모드를 <전체 반복|기본>으로 변경합니다.", false);
        embedBuilder.addField(START_TAG + "queue(q)", "현재 음악 재생목록을 표시합니다.", false);
        embedBuilder.addField(START_TAG + "mclear", "현재 음악 재생목록을 비웁니다.", false);
        embedBuilder.addField(START_TAG + "skip(s)", "현재 재생 중인 음악을 건너 뛰고 다음 곡을 재생합니다.", false);

        textChannel.sendMessageEmbeds(embedBuilder.build()).queue();
    }

    private void musicClear(){
        musicStreamSystem.clearTracksOfQueue(textChannel);
        sendMessage("트랙 리스트를 비웠습니다.");
    }

    private void musicSkip(){
        musicStreamSystem.skipCurrentTracksOfQueue(textChannel);
        sendMessage("재생 중인 트랙을 스킵했습니다.");
    }


    /* Internal Util Functions */
    public void sendMessage(String msg){
        if(textChannel == null){
            print("Channel Not Allocated.");
        }else if(textChannel.canTalk()){
            textChannel.sendMessage(msg).queue();
        } else {
            print("Can't talk to this channel");
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

    private void deleteCurrentUserCommand(){
        if(currentUserCommand == null) return;
        currentUserCommand.delete().queue();
        currentUserCommand = null;
    }

    private void deletePreviousUserCommand(){
        if(previousUserCommand == null) return;
        previousUserCommand.delete().queue();
        previousUserCommand = null;
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
    private void printn(Object o){
        System.out.print(o);
    }

    private TextStyleManager textStyler = new TextStyleManager();
}