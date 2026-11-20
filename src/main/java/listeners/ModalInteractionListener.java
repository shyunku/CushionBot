package listeners;

import Utilities.Util;
import core.Service;
import exceptions.GuildManagerNotFoundException;
import exceptions.RecruitPublishException;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.recruit.Recruit;
import service.recruit.RecruitManager;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class ModalInteractionListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ModalInteractionListener.class);

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            event.reply("서버 안에서만 사용할 수 있습니다.").setEphemeral(true).queue();
            return;
        }

        if (event.getModalId().equals("recruitModal")) {
            try {
                registerRecruit(event, guild, member);
            } catch (IllegalArgumentException | RecruitPublishException e) {
                replyError(event, e.getMessage());
            } catch (Exception e) {
                logger.error("Recruit registration failed", e);
                replyError(event, "구인 등록 중 오류가 발생했습니다.");
            }
        }
    }

    private void registerRecruit(ModalInteractionEvent event, Guild guild, Member member) throws GuildManagerNotFoundException, RecruitPublishException {
        RecruitManager recruitManager = Service.GetRecruitManagerByGuildId(guild.getId());
        if (recruitManager == null || recruitManager.recruitChannel == null) {
            replyError(event, "구인 채널이 설정되지 않았습니다. /구인채널생성 명령어로 설정해주세요.");
            return;
        }

        ModalMapping gameNameInput = event.getValue("gameName");
        ModalMapping recruitNumInput = event.getValue("recruitNum");
        ModalMapping timeInput = event.getValue("time");
        ModalMapping durationInput = event.getValue("duration");

        if (gameNameInput == null || gameNameInput.getAsString().isBlank()) {
            replyError(event, "게임 이름을 입력해주세요.");
            return;
        }

        int recruitingNum = 0;
        if (recruitNumInput != null && !recruitNumInput.getAsString().isBlank()) {
            try {
                recruitingNum = Integer.parseInt(recruitNumInput.getAsString().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("모집 인원은 숫자로 입력해주세요. (ex. 5)");
            }
            if (recruitingNum <= 0) throw new IllegalArgumentException("모집 인원은 2명 이상이어야 합니다.");
        }

        long recruitAt = 0;
        if (timeInput != null && !timeInput.getAsString().isBlank()) {
            try {
                LocalTime time = LocalTime.parse(timeInput.getAsString().trim(), DateTimeFormatter.ofPattern("H:mm"));
                recruitAt = Recruit.nextKstTime(time.getHour(), time.getMinute());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("시간 형식이 올바르지 않습니다. (ex. 21:30)");
            }
        }

        double hours = 3;
        if (durationInput != null && !durationInput.getAsString().isBlank()) {
            try {
                hours = Double.parseDouble(durationInput.getAsString().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("소요 시간은 시간 단위 숫자로 입력해주세요. (ex. 1시간 반 -> 1.5)");
            }
            if (hours <= 0 || hours > 48) throw new IllegalArgumentException("소요 시간은 0보다 크고 48시간 이하로 입력해주세요.");
        }
        long duration = (long) (hours * 60 * 60 * 1000);

        String recruitKey = Util.generateRandomNumber(4);
        Recruit recruit = new Recruit(guild, recruitKey, member, gameNameInput.getAsString().trim(), recruitingNum,
                System.currentTimeMillis(), recruitAt, duration);
        recruitManager.registerRecruit(member, recruit);
        event.reply(String.format("구인이 등록되었습니다. 코드: %s", recruitKey)).queue();
    }

    private void replyError(ModalInteractionEvent event, String message) {
        if (event.isAcknowledged()) {
            event.getHook().sendMessage(message).setEphemeral(true).queue();
        } else {
            event.reply(message).setEphemeral(true).queue();
        }
    }
}
