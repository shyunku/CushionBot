package listeners;

import Utilities.TimeUtil;
import Utilities.Util;
import core.Service;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.jetbrains.annotations.NotNull;
import service.recruit.Recruit;
import service.recruit.RecruitManager;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.Objects;

public class ModalInteractionListener extends ListenerAdapter {
    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        Guild guild = event.getGuild();
        String guildId = Objects.requireNonNull(guild).getId();
        Member member = event.getMember();
        if (member == null) return;

        switch (event.getModalId()) {
            case "recruitModal":
                try {
                    RecruitManager recruitManager = Service.GetRecruitManagerByGuildId(guild.getId());
                    if (recruitManager == null || recruitManager.recruitChannel == null) {
                        event.reply("구인 채널이 설정되지 않았습니다. /구인채널 명령어로 설정해주세요.").queue();
                        return;
                    }

                    ModalMapping gameNameInput = event.getValue("gameName");
                    ModalMapping recruitNumInput = event.getValue("recruitNum");
                    ModalMapping timeInput = event.getValue("time");
                    ModalMapping durationInput = event.getValue("duration");

                    if (gameNameInput == null) {
                        event.reply("게임 이름을 입력해주세요.").queue();
                        return;
                    }

                    if (recruitNumInput != null && recruitNumInput.getAsString().trim().equals("0")) {
                        event.reply("모집 인원은 2명 이상이어야 합니다.").queue();
                        return;
                    }

                    String gameName = gameNameInput.getAsString();
                    int recruitingNum = recruitNumInput == null || recruitNumInput.getAsString().isEmpty() ? 0 : Util.parseInt(recruitNumInput.getAsString());
                    int hour = -1;
                    int minute = -1;

                    if (timeInput != null && !timeInput.getAsString().isEmpty()) {
                        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                        try {
                            LocalTime time = LocalTime.parse(timeInput.getAsString(), timeFormatter);
                            hour = time.getHour();
                            minute = time.getMinute();
                        } catch (DateTimeParseException e) {
                            throw new IllegalArgumentException("시간 형식이 올바르지 않습니다: (e.g., 09:30).");
                        }
                    }

                    long registeredAt = System.currentTimeMillis();
                    long duration = (long) (
                            (durationInput == null || durationInput.getAsString().isEmpty() ? 1
                                    : Util.parseDouble(durationInput.getAsString())) * 60 * 60 * 1000);

                    Calendar current = TimeUtil.getKstCalendar();
                    Calendar recruitAt = TimeUtil.getKstCalendar();
                    recruitAt.set(Calendar.HOUR_OF_DAY, hour);
                    recruitAt.set(Calendar.MINUTE, minute);
                    recruitAt.set(Calendar.SECOND, 0);

                    if (recruitAt.before(current)) {
                        recruitAt.add(Calendar.DATE, 1);
                    }

                    boolean isTimeSpecified = hour != -1;

                    String recruitKey = Util.generateRandomNumber(4);
                    Recruit recruit = new Recruit(guild, recruitKey, member, gameName, recruitingNum, registeredAt, isTimeSpecified ? recruitAt.getTimeInMillis() : 0, duration);
                    recruitManager.registerRecruit(member, recruit);
                    event.reply(String.format("구인이 등록되었습니다. 코드: %s", recruitKey)).queue(m -> {
                        m.deleteOriginal().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    event.getHook().sendMessage("구인 등록 중 오류가 발생했습니다: " + e.getMessage()).queue(m -> {
                        m.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS);
                    });
                }
                break;
        }
    }
}
