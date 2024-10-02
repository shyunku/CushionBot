package Utilities;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;

public class TokenManager {
    public static final String BOT_CLIENT_ID = new TokenManager().getBotClientID();
    public static final String RSO_CLIENT_ID = new TokenManager().getCredential("teamgg", "rso_client_id");
    public static final String RSO_REDIRECT_URI = new TokenManager().getCredential("teamgg", "rso_redirect_uri");

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

    private String getCredential(String category, String key) {
        Yaml yaml = new Yaml();
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(new File("src/main/resources/credentials.yaml"));
            Map<String, Object> maps = yaml.load(inputStream);
            Map<String, Object> tokens = (Map<String, Object>) maps.get(category);

            return tokens.get(key).toString();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return null;
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
