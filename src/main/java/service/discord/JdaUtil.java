package service.discord;

import core.CushionBot;
import exceptions.AudioChannelNotFoundException;
import exceptions.MemberNotFoundException;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdaUtil {
    private static final Logger logger = LoggerFactory.getLogger(JdaUtil.class);

    public static void LeaveCurrentAudioChannel(Guild guild) {
        try {
//            GuildVoiceState voiceState = guild.getSelfMember().getVoiceState();
//            if (voiceState == null) {
//                throw new Exception("voiceState is null");
//            }
//            AudioChannel connectedChannel = voiceState.getChannel();
//            if (connectedChannel != null) {
//                guild.getAudioManager().closeAudioConnection();
//            }
            CushionBot.jda.getDirectAudioController().disconnect(guild);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void JoinAudioChannel(AudioChannel audioChannel) throws AudioChannelNotFoundException {
        if (audioChannel == null) throw new AudioChannelNotFoundException();
        CushionBot.jda.getDirectAudioController().connect(audioChannel);
    }

    public static AudioChannel GetUserAudioChannel(Member member) throws MemberNotFoundException {
        if (member == null) throw new MemberNotFoundException();
        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null) return null;
        return voiceState.getChannel();
    }
}
