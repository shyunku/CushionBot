package core.command;

import Utilities.TextStyleManager;
import Utilities.TokenManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import core.Service;
import music.Core.MusicStreamSystem;
import music.object.MusicPlayMode;
import music.object.YoutubeTrackInfo;
import music.tools.YoutubeCrawler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.managers.AudioManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;

public class GuildCommandRouter {
    private final Logger logger = LoggerFactory.getLogger(GuildCommandRouter.class);

    private Guild guild;

    private CommandParser commandParser = new CommandParser();
    private MusicStreamSystem musicStreamSystem = new MusicStreamSystem();
    private AudioPlayerManager audioPlayerManager = new DefaultAudioPlayerManager();
    private AudioManager audioManager = null;
    private YoutubeCrawler youtubeCrawler = new YoutubeCrawler();

    private TextStyleManager textStyler = new TextStyleManager();

    public GuildCommandRouter(Guild guild) {
        this.guild = guild;

        AudioSourceManagers.registerRemoteSources(audioPlayerManager);
        AudioSourceManagers.registerLocalSource(audioPlayerManager);
    }

    public void route(MessageReceivedEvent e) {
        User user = e.getAuthor();
        TextChannel textChannel = e.getTextChannel();
        Message message = e.getMessage();

        String userId = user.getId();
        String guildId = guild.getId();
        String rawMessage = message.getContentDisplay();
        boolean isMusicChannel = false;

        if(Service.guildMusicChannelMap.containsKey(guildId)) {
            String musicChannelId = Service.guildMusicChannelMap.get(guildId);
            if (textChannel.getId().equals(musicChannelId)) {
                isMusicChannel = true;
            }
        }

        // exclude empty string
        if(rawMessage.length() == 0) return;

        // handle bot message
        if(user.isBot() && userId.equals(TokenManager.BOT_CLIENT_ID)) {
            logger.info(String.format("BOT Message --- %s", rawMessage));
            return;
        }

        commandParser.setThis(rawMessage, false);
        ArrayList<String> segments = commandParser.getSegments();
        String sentence = commandParser.getIntegratedString();
        String keyword = commandParser.getKeyword();

        musicStreamSystem.registerMusicStreamer(this.audioManager, audioPlayerManager, textChannel);

        if(isMusicChannel) {
            this.musicQuickPlay(e, rawMessage);
        }
    }

    private void join(MessageReceivedEvent e) {
        try {
            TextChannel textChannel = e.getTextChannel();
            AudioChannel voiceChannel = e.getMember().getVoiceState().getChannel();
            if(voiceChannel == null){
                sendMessage(textChannel, "음악을 재생하시려면 음성채널에 먼저 입장해주세요!");
                return;
            }

            this.audioManager = guild.getAudioManager();
            if(this.audioManager.getConnectionStatus() == ConnectionStatus.CONNECTING_ATTEMPTING_UDP_DISCOVERY){
                sendMessage(textChannel, "봇이 이미 입장 중이에요!");
            }else{
                this.audioManager.openAudioConnection(voiceChannel);
            }
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    private void musicQuickPlay(MessageReceivedEvent e, String searchKeyword){
        TextChannel textChannel = e.getTextChannel();
        AudioChannel currentVoiceChannel = guild.getSelfMember().getVoiceState().getChannel();
        if(currentVoiceChannel == null){
            join(e);
        }

        if(searchKeyword.contains("www.youtube.com")){
            musicStreamSystem.addTrackListToQueue(textChannel, audioPlayerManager, searchKeyword);
        } else {
            ArrayList<YoutubeTrackInfo> trackCandidates = youtubeCrawler.getVideoCandidates(searchKeyword);
            if(trackCandidates.isEmpty()){
                sendMessage(textChannel, String.format("\"%s\"에 대한 검색 결과가 없습니다.", searchKeyword));
                return;
            }

            YoutubeTrackInfo selectedTrackInfo = youtubeCrawler.getVideoCandidates(searchKeyword).get(0);
            musicStreamSystem.addTrackToQueue(textChannel, audioPlayerManager, selectedTrackInfo);
        }

        MessageHistory messageHistory = textChannel.getHistory();
        messageHistory.retrievePast(30).queue(messageList -> {
            textChannel.deleteMessages(messageList).queue();
            this.printMusicBox(e);
        });
    }

    private void printMusicBox(MessageReceivedEvent e) {
        TextChannel textChannel = e.getTextChannel();

        // add music box
        ArrayList<YoutubeTrackInfo> trackInfoList = musicStreamSystem.getMusicStreamer(textChannel).getTrackScheduler().getTrackDataList();
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Current Track List");
        embedBuilder.setDescription("현재 "+trackInfoList.size()+"개의 트랙이 있습니다. - 재생모드: "
                +textStyler.toBold(musicStreamSystem.getPlayModeDescription(textChannel)));
        embedBuilder.setColor(new Color(0, 255, 187));
        ArrayList<StringBuilder> stringBuilders = new ArrayList<>();
        StringBuilder trackInfoListBlock = new StringBuilder();

        int index = 1;
        for(YoutubeTrackInfo trackInfo : trackInfoList){
            int nextTotalStringLen = trackInfoListBlock.toString().length() + 7 + trackInfo.getTitle().length();
            if(nextTotalStringLen >= 1024){
                stringBuilders.add(trackInfoListBlock);
                trackInfoListBlock = new StringBuilder();
            }
            trackInfoListBlock.append(index++).append(". ").append(trackInfo.getTitle());
            if(index <= trackInfoList.size())
                trackInfoListBlock.append("\n");
        }
        stringBuilders.add(trackInfoListBlock);

        for(int i=0;i<stringBuilders.size();i++){
            @Nullable String tmpTitle = "트랙리스트 페이지 "+(i+1)+"/"+stringBuilders.size();
            embedBuilder.addField(textStyler.toBold(tmpTitle), stringBuilders.get(i).toString(), false);
        }

        ActionRow actionRow = ActionRow.of(
                Button.danger("musicBoxStop", "■"),
                Button.secondary("musicBoxPause", "⏸"),
                Button.primary("musicBoxPlay", "▶"),
                Button.secondary("musicBoxNext", "⏭"),
                Button.secondary("musicBoxRepeat", "\uD83D\uDD01")
        );

        e.getTextChannel().sendMessageEmbeds(embedBuilder.build()).setActionRows(actionRow).queue();
    }

    private void play(MessageReceivedEvent e, String searchKeyword){
        TextChannel textChannel = e.getTextChannel();
        AudioChannel voiceChannel = e.getMember().getVoiceState().getChannel();

        if(voiceChannel == null){
            sendMessage(textChannel, "음악을 재생하시려면 음성채널에 먼저 입장해주세요!");
            return;
        }

        if(searchKeyword.contains("www.youtube.com")){
            musicStreamSystem.addTrackListToQueue(textChannel, audioPlayerManager, searchKeyword);
            return;
        }

        ArrayList<YoutubeTrackInfo> trackCandidates = youtubeCrawler.getVideoCandidates(searchKeyword);
        if(trackCandidates.isEmpty()){
            sendMessage(textChannel, "해당 검색어의 결과가 없습니다.");
            return;
        }
        StringBuilder res = new StringBuilder();
        res.append(textStyler.toBold("명령어 " + textStyler.toBlock("$play 1-5") + "를 사용하여 재생하세요:")).append("\n");
        int index = 0;
        for (YoutubeTrackInfo info : trackCandidates) {
            res.append(textStyler.toBold(++index + "")).append(". ").append(info.getTitle()).append("\n");
        }

        textChannel.sendMessage(res.toString());
        this.musicStreamSystem.printMusicBox(textChannel);
    }

    private void leave(MessageReceivedEvent e){
        TextChannel textChannel = e.getTextChannel();
        AudioChannel connectedChannel = guild.getSelfMember().getVoiceState().getChannel();
        if(connectedChannel == null){
            sendMessage(textChannel, "나올 채널이 없습니다.");
        }else{
            guild.getAudioManager().closeAudioConnection();
        }
    }

    private void clear(MessageReceivedEvent e, String amountStr){
        TextChannel textChannel = e.getTextChannel();
        try{
            final int amount = Math.min(Integer.parseInt(amountStr), 500);

            MessageHistory messageHistory = textChannel.getHistory();
            messageHistory.retrievePast(amount).queue(messageList -> {
                textChannel.deleteMessages(messageList).queue();
                String boldStr = textStyler.toBold("최근 메시지 "+amount+"개가 "+textStyler.toBlock(e.getAuthor().getName())+"에 의해 삭제되었습니다.");

                textChannel.sendMessage(boldStr).queue();
            });
        } catch (NumberFormatException exception){
            textChannel.sendMessage("잘못된 인자: clear 명령이 취소되었습니다.").queue();
        }
    }

    private void repeatTrackList(MessageReceivedEvent e, ArrayList<String> segments){
        TextChannel textChannel = e.getTextChannel();
        MusicPlayMode musicPlayMode = MusicPlayMode.NORMAL;
        if(segments.isEmpty()){
            musicPlayMode = MusicPlayMode.REPEAT_SINGLE;
            textChannel.sendMessage("재생 모드가 현재 트랙 반복으로 변경되었습니다.").queue();
        }else{
            String segment = segments.get(0);
            switch(segment){
                case "all":
                    musicPlayMode = MusicPlayMode.REPEAT_ALL;
                    textChannel.sendMessage("재생 모드가 전체 반복으로 변경되었습니다.").queue();
                    break;
                case "off":
                    textChannel.sendMessage("재생 모드가 기본으로 변경되었습니다.").queue();
                    //NORMAL 모드로 정상화
                    break;
                default:
                    //기타 세그먼트는 무시
                    return;
            }
        }
        musicStreamSystem.repeatTrackToQueue(textChannel, musicPlayMode);
    }

    private void musicQueue(MessageReceivedEvent e){
        TextChannel textChannel = e.getTextChannel();
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

    private void musicClear(MessageReceivedEvent e){
        TextChannel textChannel = e.getTextChannel();
        musicStreamSystem.clearTracksOfQueue(textChannel);
        textChannel.sendMessage("트랙 리스트를 비웠습니다.");
    }

    private void musicSkip(MessageReceivedEvent e){
        TextChannel textChannel = e.getTextChannel();
        musicStreamSystem.skipCurrentTracksOfQueue(textChannel);
        textChannel.sendMessage("재생 중인 트랙을 스킵했습니다.");
    }

    public MusicStreamSystem getMusicStreamSystem(){
        return musicStreamSystem;
    }

    private void sendMessage(TextChannel textChannel, String msg){
        if(textChannel == null){
            print("Channel Not Allocated.");
        }else if(textChannel.canTalk()){
            textChannel.sendMessage(msg).queue();
        } else {
            print("Can't talk to this channel");
        }
    }

    private void print(Object o){
        System.out.println(o);
    }
}
