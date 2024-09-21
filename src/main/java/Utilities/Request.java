package Utilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class Request {
    private static final Logger logger = LoggerFactory.getLogger(Request.class);

    public static <ResType> ResType get(String rawUrl, Class<ResType> responseType) throws IOException, URISyntaxException, InterruptedException {
        String url = encodeURIComponent(rawUrl);
        logger.info("[GET] {}", url);
        ObjectMapper mapper = new ObjectMapper();
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Request failed with status code: " + response.statusCode());
        }
        if (response.body() == null) {
            return null;
        }
        logger.info("[RESPONSE] {}", response.body());
        return mapper.readValue(response.body(), responseType);
    }

    public static <ResType> ResType post(String rawUrl, Object reqBody, Class<ResType> responseType) throws IOException, URISyntaxException, InterruptedException {
        String url = encodeURIComponent(rawUrl);
        logger.info("[POST] {}", url);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(reqBody);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)) // POST 요청과 본문 설정
                .header("Content-Type", "application/json") // JSON 타입 설정
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Request failed with status code: " + response.statusCode());
        }
        if (response.body() == null) {
            return null;
        }
        logger.info("[RESPONSE] {}", response.body());
        return mapper.readValue(response.body(), responseType);
    }

    public static String encodeURIComponent(String url) throws UnsupportedEncodingException {
        return URLEncoder.encode(url, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%3A", ":")
                .replace("%2F", "/")
                .replace("%3F", "?")
                .replace("%3D", "=")
                .replace("%26", "&")
                .replace("%21", "!")
                .replace("%27", "'")
                .replace("%28", "(")
                .replace("%29", ")")
                .replace("%7E", "~");
    }
}
