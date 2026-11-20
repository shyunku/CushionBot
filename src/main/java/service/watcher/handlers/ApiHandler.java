package service.watcher.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import core.CushionBot;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.watcher.GuildWatcher;

import java.io.IOException;

/**
 * Dashboard API.
 * GET /api/guilds                 guilds with the number of people connected now
 * GET /api/guilds/{id}/snapshot   guild info, member info and all sessions of one guild
 */
public class ApiHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                HttpUtil.sendText(exchange, 405, "Method Not Allowed");
                return;
            }
            if (CushionBot.jda == null || !GuildWatcher.isInitialized()) {
                HttpUtil.sendText(exchange, 503, "Bot is starting");
                return;
            }

            String[] parts = exchange.getRequestURI().getPath().replaceAll("/+$", "").split("/");
            // ["", "api", "guilds", "{id}", "snapshot"]
            if (parts.length == 3 && parts[2].equals("guilds")) {
                HttpUtil.sendJson(exchange, mapper.writeValueAsString(GuildWatcher.guildList()));
                return;
            }
            if (parts.length == 5 && parts[2].equals("guilds") && parts[4].equals("snapshot")) {
                Guild guild = CushionBot.jda.getGuildById(parts[3]);
                if (guild == null) {
                    HttpUtil.sendText(exchange, 404, "Guild not found");
                    return;
                }
                HttpUtil.sendJson(exchange, mapper.writeValueAsString(GuildWatcher.snapshot(guild)));
                return;
            }
            HttpUtil.sendText(exchange, 404, "Not Found");
        } catch (Exception e) {
            logger.error("Watcher API error: {}", exchange.getRequestURI(), e);
            HttpUtil.sendText(exchange, 500, "Internal Server Error");
        }
    }
}
