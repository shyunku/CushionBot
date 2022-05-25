package Utilities;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.Iterator;
import java.util.Map;

public class TokenManager {
    private String getObjectToken(String key){
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

    private String getObjectId(String key){
        Yaml yaml = new Yaml();
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(new File("src/main/resources/credentials.yaml"));
            Map<String, Object> maps = yaml.load(inputStream);
            Map<String, Object> tokens = (Map<String, Object>) maps.get("keys");

            return tokens.get(key).toString();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    public String getDiscordBotToken(){
        return getObjectToken("discord_bot_token");
    }

    public String getGoogleApiToken(){
        return getObjectToken("google_api_token");
    }

    public String getBotClientID(){
        return getObjectToken("bot_client_id");
    }
}
