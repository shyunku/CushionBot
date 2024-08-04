package service.watcher;

import Utilities.Util;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import core.CushionBot;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class WatchServer {
    public static void start() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(7918), 0);
        server.createContext("/data", new DataHandler());
        server.createContext("/guild", new GuildHandler());
        server.createContext("/user", new UserHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Watch server started at port 7918");
    }

    static class IntermediateHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }
    }

    static class DataHandler extends IntermediateHttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                super.handle(exchange);
                String dataJson = GuildWatcher.getAccessLogs();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, dataJson.getBytes().length);

                OutputStream os = exchange.getResponseBody();
                os.write(dataJson.getBytes());
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }
    }

    static class GuildHandler extends IntermediateHttpHandler {
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
                exchange.getResponseHeaders().set("Cache-Control", "max-age=3600");
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

    static class UserHandler extends IntermediateHttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                super.handle(exchange);
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");
                if (parts.length < 4) {
                    String response = "404 (Not Found)\n";
                    exchange.sendResponseHeaders(404, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                    return;
                }

                String guildId = parts[2];
                String userId = parts[3];
                JDA jda = CushionBot.jda;
                Guild guild = jda.getGuildById(guildId);
                if (guild == null) throw new Exception("Guild not found: " + guildId);
                Member member = guild.retrieveMemberById(userId).complete();
                if (member == null) throw new Exception("Member not found " + userId + " in guild " + guildId);
                UserInfo userInfo = new UserInfo(member.getId(), member.getNickname(), member.getEffectiveName(), member.getUser().getEffectiveAvatarUrl());
                String dataJson = Util.ToJson(userInfo, false);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Cache-Control", "max-age=3600");
                exchange.sendResponseHeaders(200, dataJson.getBytes().length);

                OutputStream os = exchange.getResponseBody();
                os.write(dataJson.getBytes());
                os.close();
            } catch (Exception e) {
                String response = "500 (Internal Server Error)\n" + e.getMessage();
                exchange.sendResponseHeaders(500, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }
}
