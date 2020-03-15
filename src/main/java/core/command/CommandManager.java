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

public class CommandManager {
    private TextChannel textChannel;
    private Guild guild;
    private final boolean SAFE_MODE = true;
    private CommandStatus mode = CommandStatus.NORMAL;
    private YoutubeCrawler youtubeCrawler = new YoutubeCrawler();

    //Memories
    private ArrayList<String> kickList = new ArrayList<>();


    public void parseCommand(MessageReceivedEvent e){
        User user = e.getAuthor();
        textChannel = e.getTextChannel();
        Message msg = e.getMessage();
        String text = msg.getContentDisplay();
        guild = e.getGuild();

        CommandParser commandParser = new CommandParser(text);
        ArrayList<String> segments = commandParser.getSegments();
        String sentence = commandParser.getIntegratedString();
        String keyword = commandParser.getKeyword();

        print(user.getName()+": "+text+ " [Channel "+textChannel.getId() + "/"+textChannel.getName()+"]");

        switch(mode){
            case NORMAL:
                switch(keyword){
                    case "alive": alive(); break;
                    case "join": join(e); break;
                    case "leave": leave(); break;
                    case "say": say(sentence, msg); break;
                    case "kick": kick(segments); break;
                    case "find": find(sentence); break;
                }
                break;
            case ASK_KICK:
                if(keyword.equals("yes")){
                    for (String id : kickList) {
                        guild.kick(guild.getMemberById(id)).queue();
                    }
                }else{
                    textChannel.sendMessage("kicking canceled").queue();
                }
                break;
        }

    }

    /* Commands Execution */
    private void alive(){
        sendMessage("I'm alive.");
    }

    private void join(MessageReceivedEvent e){
        VoiceChannel voiceChannel = e.getMember().getVoiceState().getChannel();
        sendMessage("채널 "+voiceChannel.getName()+"에 참여 중");
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

    private void play(){
        VoiceChannel currentVoiceChannel = guild.getSelfMember().getVoiceState().getChannel();
        if(currentVoiceChannel == null){
            sendMessage("현재 봇이 참여한 음성 채널이 없습니다.");
        }else {
//            AudioPlayer audioPlayer = playerManager.createPlayer();
//            TrackScheduler trackScheduler = new TrackScheduler(audioPlayer);
//            audioPlayer.addListener(trackScheduler);
//            playerManager.loadItem(getIntegratedString(segment), new AudioLoadResultHandler() {
//                @Override
//                public void trackLoaded(AudioTrack track) {
//                    trackScheduler.queue(track);
//                    sendMessage(track.getInfo().title + " added to queue.");
//                }
//
//                @Override
//                public void playlistLoaded(AudioPlaylist playlist) {
//                    sendMessage("playlist loaded.");
//                }
//
//                @Override
//                public void noMatches() {
//                    sendMessage("매치 결과 없음");
//                }
//
//                @Override
//                public void loadFailed(FriendlyException exception) {
//                    sendMessage("로드 실패 씨발");
//                }
//            });
        }
    }

    private void find(String searchKeyword){
        ArrayList<YoutubeTrackInfo> trackInfoBundle = youtubeCrawler.getVideoCandidates(searchKeyword);
        StringBuilder res = new StringBuilder();
        int index = 0;
        for(YoutubeTrackInfo info : trackInfoBundle){
            res.append(textStyler.toBold(++index+"")).append(". ").append(info.getTitle()).append("\n");
        }

        sendBoldMessage("명령어 "+textStyler.toBlock("$play 1-5")+"를 사용하여 재생:");
        sendMessage(res.toString());
    }


    /* Internal Util Functions */
    private void sendMessage(String msg){
        textChannel.sendMessage(msg).queue();
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
        sendMessage("현재 봇이 안전모드에 있습니다. 위험한 명령어는 제한됩니다.");
        return true;
    }

    private void print(Object o){
        System.out.println(o);
    }

    private TextStyleManager textStyler = new TextStyleManager();
}