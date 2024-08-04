package service.watcher;

import Utilities.Util;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import core.CushionBot;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class WatchServer {
    public static void start() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(7918), 0);
        server.createContext("/watch", new WatchHandler());
        server.createContext("/data", new DataHandler());
        server.createContext("/guild", new GuildHandler());
        server.createContext("/user", new UserHandler());
        server.createContext("/static/styles", new StaticStyleHandler());
        server.createContext("/static/scripts", new StaticScriptHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Watch server started at port 7918");
    }

    static class WatchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                InputStream inputStream = getClass().getClassLoader().getResourceAsStream("templates/watcher.html");
                if (inputStream == null) {
                    String response = "404 (Not Found)\n";
                    exchange.sendResponseHeaders(404, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                    return;
                }

                String dataJson = GuildWatcher.getAccessLogs();
                ObjectMapper mapper = new ObjectMapper();
                String response = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                response = response.replace("$$data", dataJson);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }
    }

    static class DataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
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

    static class GuildHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
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

    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
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

    static class StaticStyleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                String filePath = path.substring(1);
                InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filePath);
                if (inputStream == null) {
                    String response = "404 (Not Found)\n";
                    exchange.sendResponseHeaders(404, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                    return;
                }

                byte[] bytes = inputStream.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/css");
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }
    }

    static class StaticScriptHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                String filePath = path.substring(1);
                InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filePath);
                if (inputStream == null) {
                    String response = "404 (Not Found)\n";
                    exchange.sendResponseHeaders(404, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                    return;
                }

                byte[] bytes = inputStream.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/javascript");
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }
    }
}
