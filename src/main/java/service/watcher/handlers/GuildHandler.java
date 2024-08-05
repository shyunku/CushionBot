package service.watcher.handlers;

import Utilities.Util;
import com.sun.net.httpserver.HttpExchange;
import core.CushionBot;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import service.watcher.GuildInfo;

import java.io.IOException;
import java.io.OutputStream;

public class GuildHandler extends IntermediateHttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            super.handle(exchange);
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            if (parts.length < 3) {
                String response = "404 (Not Found)\n";
                exchange.sendResponseHeaders(404, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }

            String guildId = parts[2];
            JDA jda = CushionBot.jda;
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) throw new Exception("Guild not found");
            GuildInfo guildInfo = new GuildInfo(guild.getId(), guild.getName(), guild.getMemberCount(), guild.getIconUrl());
            String dataJson = Util.ToJson(guildInfo, false);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Cache-Control", "max-age=86400");
            exchange.sendResponseHeaders(200, dataJson.getBytes().length);

            OutputStream os = exchange.getResponseBody();
            os.write(dataJson.getBytes());
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
            String response = "500 (Internal Server Error)\n" + e.getMessage();
            exchange.sendResponseHeaders(500, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
}