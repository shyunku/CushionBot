package service.teamgg;

import Utilities.Request;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /팀지지라인: an ephemeral Components V2 panel to change one's team.gg lane preferences and masteries.
 * Each lane has a text line (icon, preference, mastery) and a button row; [저장] sends what changed,
 * [되돌리기] restores the loaded values and [취소] closes the panel.
 */
public class LanePanel {
    private static final Logger logger = LoggerFactory.getLogger(LanePanel.class);

    public static final String PREFIX = "tgl:";
    private static final String[] LANES = {"탑", "정글", "미드", "원딜", "서폿"};
    private static final String[] LANE_EMOJIS = {"lane_top", "lane_jungle", "lane_mid", "lane_bottom", "lane_support"};
    /** Preference -1..2 */
    private static final String[] PREF_WORDS = {"싫음", "보통", "좋음", "주라인"};
    private static final String[] PREF_EMOJIS = {"pref_dislike", "pref_normal", "pref_like", "pref_main"};
    private static final String[] PREF_FALLBACK = {"🔴", "🟡", "🟢", "🔵"};
    private static final int ACCENT = 0x3ddc97;
    /** Ideographic space: about the width of one Hangul syllable, used to line the columns up. */
    private static final String IDEO = "　";
    private static final long STALE_MS = 60 * 60 * 1000L;

    private static class State {
        final String token = UUID.randomUUID().toString().substring(0, 8);
        final String userId;
        final String name;
        final String riotId;
        final int accounts;
        int[] savedFavors;
        int[] savedMasteries;
        int[] favors;
        int[] masteries;
        String status;
        final long createdAt = System.currentTimeMillis();

        State(String userId, String name, TeamggApi.Lines lines) {
            this.userId = userId;
            this.name = name;
            this.riotId = lines.riotId;
            this.accounts = lines.accounts;
            // keep values inside the ranges the panel can show
            this.savedFavors = Arrays.stream(lines.favors).map(v -> Math.max(-1, Math.min(2, v))).toArray();
            this.savedMasteries = Arrays.stream(lines.masteries).map(v -> Math.max(0, Math.min(5, v))).toArray();
            this.favors = savedFavors.clone();
            this.masteries = savedMasteries.clone();
        }

        boolean dirty() {
            return !Arrays.equals(favors, savedFavors) || !Arrays.equals(masteries, savedMasteries);
        }
    }

    /** One panel per user; opening a new one makes the old one's buttons stale. */
    private static final Map<String, State> panels = new ConcurrentHashMap<>();

    public static void open(SlashCommandInteractionEvent e) {
        String userId = e.getUser().getId();
        String name = e.getMember() != null ? e.getMember().getEffectiveName() : e.getUser().getEffectiveName();
        e.deferReply(true).queue();
        e.getHook().setEphemeral(true);
        panels.values().removeIf(s -> System.currentTimeMillis() - s.createdAt > STALE_MS);
        CompletableFuture.runAsync(() -> {
            TeamggApi.Lines lines;
            try {
                lines = TeamggApi.fetchLines(userId);
            } catch (Exception err) {
                logger.error("team.gg line lookup failed", err);
                e.getHook().editOriginal(errorMessage(err)).queue();
                return;
            }
            if (lines == null) {
                e.getHook().editOriginal("라이엇 계정이 연동되지 않았습니다. 먼저 /팀지지등록 명령어로 연동해주세요.").queue();
                return;
            }
            State state = new State(userId, name, lines);
            panels.put(userId, state);
            e.getHook().editOriginalComponents(build(state)).useComponentsV2().queue();
        });
    }

    public static void onButton(ButtonInteractionEvent e) {
        String[] parts = e.getComponentId().split(":");
        State state = panels.get(e.getUser().getId());
        if (state == null || parts.length < 3 || !parts[1].equals(state.token)) {
            e.reply("이 패널은 만료되었습니다. /팀지지라인 을 다시 실행해주세요.").setEphemeral(true).queue();
            return;
        }
        String action = parts[2];
        int lane = parts.length > 3 ? Integer.parseInt(parts[3]) : -1;
        switch (action) {
            case "pd": state.favors[lane] = Math.max(-1, state.favors[lane] - 1); break;
            case "pu": state.favors[lane] = Math.min(2, state.favors[lane] + 1); break;
            case "md": state.masteries[lane] = Math.max(0, state.masteries[lane] - 1); break;
            case "mu": state.masteries[lane] = Math.min(5, state.masteries[lane] + 1); break;
            case "undo":
                state.favors = state.savedFavors.clone();
                state.masteries = state.savedMasteries.clone();
                break;
            case "cancel":
                panels.remove(state.userId);
                e.editComponents(Container.of(TextDisplay.of("설정을 취소했어요. 바꾼 값은 저장되지 않았어요.")).withAccentColor(ACCENT))
                        .useComponentsV2().queue();
                return;
            case "save":
                save(e, state);
                return;
            default:
                e.deferEdit().queue();
                return;
        }
        state.status = null;
        e.editComponents(build(state)).useComponentsV2().queue();
    }

    private static void save(ButtonInteractionEvent e, State state) {
        e.deferEdit().queue();
        int[] favors = state.favors.clone();
        int[] masteries = state.masteries.clone();
        CompletableFuture.runAsync(() -> {
            try {
                if (!Arrays.equals(favors, state.savedFavors)) {
                    TeamggApi.saveFavors(state.userId, favors);
                    state.savedFavors = favors;
                }
                if (!Arrays.equals(masteries, state.savedMasteries)) {
                    TeamggApi.saveMasteries(state.userId, masteries);
                    state.savedMasteries = masteries;
                }
                String at = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("HH:mm"));
                state.status = "✓ " + at + " 저장됨 · 들어가 있는 내전에도 반영됐어요";
            } catch (Exception err) {
                logger.error("team.gg line save failed", err);
                state.status = "저장 실패: " + errorMessage(err);
            }
            e.getHook().editOriginalComponents(build(state)).useComponentsV2().queue();
        });
    }

    private static String errorMessage(Exception err) {
        if (err instanceof Request.HttpStatusException) {
            int status = ((Request.HttpStatusException) err).status;
            if (status == 401) return "team.gg 연동 키가 맞지 않습니다. 관리자에게 알려주세요.";
            if (status == 423) return "team.gg가 내전 조합을 계산 중입니다. 잠시 후 다시 저장해주세요.";
            if (status == 404) return "라이엇 계정이 연동되지 않았습니다. 먼저 /팀지지등록 명령어로 연동해주세요.";
        }
        return "team.gg 서버에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.";
    }

    private static Container build(State s) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("### " + s.name + " · 라인 선호도 및 숙련도 설정\n"
                + "선호도는 가고 싶은 정도(싫음~주라인), 숙련도는 잘하는 정도(0~5)."));
        for (int i = 0; i < LANES.length; i++) {
            children.add(TextDisplay.of(line(s, i)));
            String id = PREFIX + s.token + ":";
            children.add(ActionRow.of(
                    Button.secondary(id + "pd:" + i, "선호 −").withDisabled(s.favors[i] <= -1),
                    Button.secondary(id + "pu:" + i, "선호 +").withDisabled(s.favors[i] >= 2),
                    Button.secondary(id + "md:" + i, "숙련 −").withDisabled(s.masteries[i] <= 0),
                    Button.secondary(id + "mu:" + i, "숙련 +").withDisabled(s.masteries[i] >= 5)));
        }
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        String source = s.riotId.isEmpty() ? "" : " (" + s.riotId + (s.accounts > 1 ? " 외 " + (s.accounts - 1) + "개 계정" : "") + ")";
        children.add(TextDisplay.of("-# " + (s.status != null ? s.status : "team.gg에 저장된 지금 값" + source + " · 고친 뒤 [저장]을 눌러야 반영돼요")));
        boolean dirty = s.dirty();
        String id = PREFIX + s.token + ":";
        children.add(ActionRow.of(
                Button.success(id + "save", "저장").withDisabled(!dirty),
                Button.secondary(id + "undo", "되돌리기").withDisabled(!dirty),
                Button.secondary(id + "cancel", "취소")));
        return Container.of(children).withAccentColor(ACCENT);
    }

    /** e.g. "<lane emoji> **탑　**　　<dot> 주라인 (2)　　<squares> (4)" with the columns lined up. */
    private static String line(State s, int i) {
        int favor = s.favors[i];
        int mastery = s.masteries[i];
        StringBuilder sb = new StringBuilder();
        String laneEmoji = LaneEmojis.get(LANE_EMOJIS[i], "");
        if (!laneEmoji.isEmpty()) sb.append(laneEmoji).append(' ');
        sb.append("**").append(pad(LANES[i], 2)).append("**").append(IDEO).append(IDEO);
        sb.append(LaneEmojis.get(PREF_EMOJIS[favor + 1], PREF_FALLBACK[favor + 1])).append(' ');
        // "(−1)" and "(2)" take the same width: a figure space follows the non-negative numbers
        sb.append(pad(PREF_WORDS[favor + 1], 3)).append(" (").append(favor < 0 ? "−" + (-favor) : String.valueOf(favor)).append(')');
        if (favor >= 0) sb.append(' ');
        sb.append(IDEO).append(IDEO);
        for (int k = 1; k <= 5; k++) {
            sb.append(k <= mastery ? LaneEmojis.get("mast_" + mastery, "■") : LaneEmojis.get("mast_0", "□"));
        }
        sb.append(" (").append(mastery).append(')');
        return sb.toString();
    }

    private static String pad(String text, int length) {
        return text + IDEO.repeat(Math.max(0, length - text.length()));
    }
}
