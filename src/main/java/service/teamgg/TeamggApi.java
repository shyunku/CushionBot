package service.teamgg;

import Utilities.Request;
import Utilities.TokenManager;
import dtos.teamgg.SetSummonerLineFavorRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/** team.gg bot APIs for line preferences ("favors", -1..2) and masteries (0..5), per lane top/jungle/mid/bottom/support. */
public class TeamggApi {
    private static final Logger logger = LoggerFactory.getLogger(TeamggApi.class);

    /** team.gg's API host (teamgg.kr itself serves the web app and answers every path with HTML). */
    public static final String BASE = "https://api.teamgg.kr";

    /** team.gg answers 423 while a custom game is computing its teams; saves are retried this many times. */
    private static final int LOCKED_RETRIES = 3;
    private static final long LOCKED_WAIT_MS = 2000;

    /** The values of one linked Riot account. */
    public static class Lines {
        public final String riotId;
        public final int[] favors;
        public final int[] masteries;
        /** Riot accounts linked to the Discord user (the values are the first one's). */
        public final int accounts;

        Lines(String riotId, int[] favors, int[] masteries, int accounts) {
            this.riotId = riotId;
            this.favors = favors;
            this.masteries = masteries;
            this.accounts = accounts;
        }
    }

    /** The line values of a Discord user's first linked Riot account, or null when no account is linked. */
    public static Lines fetchLines(String discordId) throws Exception {
        Map<?, ?> body;
        try {
            body = Request.get(BASE + "/v1/api/summonerLines?userId=" + discordId, Map.class, TokenManager.TEAMGG_BOT_API_KEY);
        } catch (Request.HttpStatusException e) {
            if (e.status == 404) return null;
            throw e;
        }
        Object accounts = body == null ? null : body.get("accounts");
        if (!(accounts instanceof List) || ((List<?>) accounts).isEmpty()) return null;
        List<?> list = (List<?>) accounts;
        Map<?, ?> first = (Map<?, ?>) list.get(0);
        Object riotId = first.get("riotId");
        return new Lines(riotId == null ? "" : riotId.toString(), ints(first.get("favors")), ints(first.get("masteries")), list.size());
    }

    private static int[] ints(Object value) {
        int[] out = new int[5];
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            for (int i = 0; i < 5 && i < list.size(); i++) out[i] = ((Number) list.get(i)).intValue();
        }
        return out;
    }

    public static void saveFavors(String discordId, int[] favors) throws Exception {
        post("/v1/api/summonerLineFavor", new SetSummonerLineFavorRequestDto(discordId, favors));
    }

    public static void saveMasteries(String discordId, int[] masteries) throws Exception {
        post("/v1/api/summonerLineMastery", Map.of("userId", discordId, "masteries", masteries));
    }

    private static void post(String path, Object body) throws Exception {
        for (int attempt = 0; ; attempt++) {
            try {
                Request.post(BASE + path, body, String.class, TokenManager.TEAMGG_BOT_API_KEY);
                return;
            } catch (Request.HttpStatusException e) {
                if (e.status != 423 || attempt >= LOCKED_RETRIES) throw e;
                logger.info("team.gg is busy computing teams; retrying {}", path);
                Thread.sleep(LOCKED_WAIT_MS * (attempt + 1));
            }
        }
    }
}
