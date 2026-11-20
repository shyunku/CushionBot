package Utilities;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;

public class TokenManager {
    public static final String BOT_CLIENT_ID = new TokenManager().getBotClientID();
    /** Bearer key for team.gg's bot-only APIs (credentials.yaml: teamgg.bot_api_key). Null when not set. */
    public static final String TEAMGG_BOT_API_KEY = new TokenManager().getCredential("teamgg", "bot_api_key");

    private String getObjectToken(String key) {
        Yaml yaml = new Yaml();
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(new File("src/main/resources/credentials.yaml"));
            Map<String, Object> maps = yaml.load(inputStream);
            Map<String, Object> tokens = (Map<String, Object>) maps.get("tokens");

            return tokens.get(key).toString();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    /** A value from a credentials.yaml section, or null when the file, section or key is missing. */
    private String getCredential(String category, String key) {
        try (InputStream inputStream = new FileInputStream(new File("src/main/resources/credentials.yaml"))) {
            Map<String, Object> maps = new Yaml().load(inputStream);
            Object section = maps == null ? null : maps.get(category);
            if (!(section instanceof Map)) return null;
            Object value = ((Map<String, Object>) section).get(key);
            return value == null ? null : value.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getDiscordBotToken() {
        return getObjectToken("discord_bot_token");
    }

    public String getGoogleApiToken() {
        return getObjectToken("google_api_token");
    }

    public String getBotClientID() {
        return getObjectToken("bot_client_id");
    }

    public boolean isProduction() {
        String productionStr = getObjectToken("production");
        if (productionStr == null) return false;
        return Boolean.parseBoolean(productionStr);
    }
}
