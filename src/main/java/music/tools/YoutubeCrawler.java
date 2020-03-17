package music.tools;

import Utilities.TokenManager;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ResourceId;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.SearchResultSnippet;
import music.object.YoutubeTrackInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class YoutubeCrawler {
    private final String API_KEY = new TokenManager().getGoogleApiToken();
    private long MAX_SEARCH = 5;

    public ArrayList<YoutubeTrackInfo> getVideoCandidates(String keyword){
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
            searchList.setMaxResults(MAX_SEARCH);

            SearchListResponse searchResponse = searchList.execute();
            List<SearchResult> searchResults = searchResponse.getItems();

            if(searchResults != null){
                for(SearchResult video : searchResults){
                    ResourceId resourceId = video.getId();
                    if(resourceId.getKind().equals("youtube#video")){
                        SearchResultSnippet snippet = video.getSnippet();
                        trackInfoBundle.add(
                                new YoutubeTrackInfo(
                                        snippet.getTitle().replaceAll("&#39;", ""),
                                        resourceId.getVideoId(),
                                        snippet.getThumbnails().getDefault().getUrl(),
                                        snippet.getChannelTitle()
                                )
                        );
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        return trackInfoBundle;
    }
}
