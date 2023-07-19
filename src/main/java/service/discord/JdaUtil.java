package service.discord;

import exceptions.AudioChannelNotFoundException;
import exceptions.MemberNotFoundException;
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.managers.AudioManager;

public class JdaUtil {
    public static void LeaveCurrentAudioChannel(Guild guild) {
        try {
            GuildVoiceState voiceState = guild.getSelfMember().getVoiceState();
            if(voiceState == null) {
                throw new Exception("voiceState is null");
            }
            AudioChannel connectedChannel = voiceState.getChannel();
            if (connectedChannel != null) {
                guild.getAudioManager().closeAudioConnection();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void JoinAudioChannel(AudioChannel audioChannel) throws AudioChannelNotFoundException {
        if(audioChannel == null) throw new AudioChannelNotFoundException();
        Guild guild = audioChannel.getGuild();
        AudioManager audioManager = guild.getAudioManager();
        ConnectionStatus status = audioManager.getConnectionStatus();
        switch (status) {
            case CONNECTED:
            case CONNECTING_ATTEMPTING_UDP_DISCOVERY:
                AudioChannel connectedChannel = audioManager.getConnectedChannel();
                assert connectedChannel != null;
                if(connectedChannel.getIdLong() != audioChannel.getIdLong()) {
                    audioManager.closeAudioConnection();
                }
                break;
        }
        audioManager.openAudioConnection(audioChannel);
    }

    public static AudioChannel GetUserAudioChannel(Member member) throws MemberNotFoundException {
        if(member == null) throw new MemberNotFoundException();
        GuildVoiceState voiceState = member.getVoiceState();
        if(voiceState == null) return null;
        return voiceState.getChannel();
    }
}
