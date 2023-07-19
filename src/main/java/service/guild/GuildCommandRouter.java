package service.guild;

import Utilities.TextStyler;
import Utilities.TokenManager;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import core.Service;
import core.command.CommandParser;
import exceptions.AudioChannelNotFoundException;
import exceptions.MemberNotFoundException;
import exceptions.MusicBoxNotFoundException;
import exceptions.MusicNotFoundException;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.discord.JdaUtil;
import service.music.Core.MusicBox;

public class GuildCommandRouter {
    private final Logger logger = LoggerFactory.getLogger(GuildCommandRouter.class);

    private Guild guild;

    private CommandParser commandParser = new CommandParser();
    private TextStyler textStyler = new TextStyler();

    public GuildCommandRouter(Guild guild) {
        this.guild = guild;
    }

    public void route(MessageReceivedEvent e, GuildManager guildManager) {
        User user = e.getAuthor();
        TextChannel textChannel = e.getTextChannel();
        Message message = e.getMessage();

        String userId = user.getId();
        String rawMessage = message.getContentDisplay();
        TextChannel musicChannel = guildManager.getMusicBox().getMusicChannel();

        // determine if this message targets to music channel
        boolean isMusicChannel = musicChannel != null && musicChannel.getId().equals(textChannel.getId());

        // exclude empty string
        if(rawMessage.length() == 0) return;

        // handle bot message
        if(user.isBot() && userId.equals(TokenManager.BOT_CLIENT_ID)) {
            logger.info(String.format("BOT Message --- %s", rawMessage));
            return;
        }

        commandParser.setThis(rawMessage, false);
//        ArrayList<String> segments = commandParser.getSegments();
//        String sentence = commandParser.getIntegratedString();
//        String keyword = commandParser.getKeyword();

        if(isMusicChannel) {
            this.musicQuickPlay(e, rawMessage);
        }
    }

    private void musicQuickPlay(MessageReceivedEvent e, String searchQuery){
        TextChannel textChannel = e.getTextChannel();
        try {
            // join audio channel if not joined
            Member requester = e.getMember();
            AudioChannel audioChannel = JdaUtil.GetUserAudioChannel(requester);
            JdaUtil.JoinAudioChannel(audioChannel);

            // play request
            MusicBox musicBox = Service.GetMusicBoxByGuildId(e.getGuild().getId());
            musicBox.quickPlay(searchQuery, requester);
            musicBox.updateMusicActionEmbed();
        } catch (MemberNotFoundException exception) {
            textChannel.sendMessage("음악 재생을 요청한 유저를 찾지 못했습니다.").queue(message -> {
                message.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS);
            });
        } catch (AudioChannelNotFoundException exception) {
            textChannel.sendMessage("음악을 재생하시려면 음성채널에 먼저 입장해주세요.").queue(message -> {
                message.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS);
            });
        } catch (MusicBoxNotFoundException exception) {
            textChannel.sendMessage("음악 실행기를 찾지 못했습니다. /music 명령어로 음악 채널을 설정해주세요.").queue(message -> {
                message.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS);
            });
        } catch (MusicNotFoundException exception) {
            textChannel.sendMessage("음악을 찾지 못했습니다.").queue(message -> {
                message.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS);
            });
        } catch (GoogleJsonResponseException exception) {
            if(exception.getDetails().getCode() == 403) {
                textChannel.sendMessage("서버 할당량이 초과되었습니다. 잠시 후 다시 시도해주세요.").queue(message -> {
                    message.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS);
                });
                logger.error("server quota exceeded: youtube data api v3 has exceeded the quota limit.");
            } else {
                exception.printStackTrace();
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            textChannel.sendMessage("예상치 못한 오류가 발생했습니다.").queue(message -> {
                message.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS);
            });
        } finally {
            // delete request message
            e.getMessage().delete().queue();
        }
    }


    private void clear(MessageReceivedEvent e, String amountStr){
        TextChannel textChannel = e.getTextChannel();
        try{
            final int amount = Math.min(Integer.parseInt(amountStr), 500);

            MessageHistory messageHistory = textChannel.getHistory();
            messageHistory.retrievePast(amount).queue(messageList -> {
                textChannel.deleteMessages(messageList).queue();
                String boldStr = textStyler.Bold("최근 메시지 "+amount+"개가 "+textStyler.Block(e.getAuthor().getName())+"에 의해 삭제되었습니다.");

                textChannel.sendMessage(boldStr).queue();
            });
        } catch (NumberFormatException exception){
            textChannel.sendMessage("잘못된 인자: clear 명령이 취소되었습니다.").queue();
        }
    }
}
