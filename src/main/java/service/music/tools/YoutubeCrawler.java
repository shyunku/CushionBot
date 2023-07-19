package service.music.tools;

import Utilities.TokenManager;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import net.dv8tion.jda.api.entities.Member;
import service.music.object.YoutubeTrackInfo;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class YoutubeCrawler {
    private static String API_KEY = new TokenManager().getGoogleApiToken();

    public static ArrayList<YoutubeTrackInfo> getVideoCandidates(String keyword, long resultCount, Member requester) throws GoogleJsonResponseException {
        ArrayList<YoutubeTrackInfo> trackInfoBundle = new ArrayList<>();

        try {
            YouTube youtube = new YouTube.Builder(new NetHttpTransport(), new JacksonFactory(), new HttpRequestInitializer() {
                @Override
                public void initialize(HttpRequest request){}
            }).setApplicationName("cushion-youtube-parser").build();
            YouTube.Search.List searchList = youtube.search().list("id,snippet");
            searchList.setKey(API_KEY);
            searchList.setQ(keyword);
            searchList.setType("video");
            searchList.setMaxResults(resultCount);

            SearchListResponse searchResponse = searchList.execute();
            List<SearchResult> searchResults = searchResponse.getItems();

            if(searchResults != null){
                for(SearchResult video : searchResults){
                    ResourceId resourceId = video.getId();
                    if(resourceId.getKind().equals("youtube#video")){
                        SearchResultSnippet snippet = video.getSnippet();

                        YouTube.Videos.List videoList = youtube.videos().list("contentDetails");
                        videoList.setId(resourceId.getVideoId());
                        videoList.setKey(API_KEY);
                        Video videoDetails = videoList.execute().getItems().get(0);
                        String durationRaw = videoDetails.getContentDetails().getDuration();
                        Duration duration = Duration.parse(durationRaw);

                        trackInfoBundle.add(
                                new YoutubeTrackInfo(
                                        snippet.getTitle().replaceAll("&#39;", ""),
                                        resourceId.getVideoId(),
                                        snippet.getThumbnails().getDefault().getUrl(),
                                        snippet.getChannelTitle(),
                                        duration,
                                        requester
                                )
                        );
                    }
                }
            }
        } catch (GoogleJsonResponseException e) {
            throw e;
        } catch (IOException e) {
            e.printStackTrace();
        }


        return trackInfoBundle;
    }
}
