package Utilities;

import net.ricecode.similarity.JaroWinklerStrategy;
import net.ricecode.similarity.SimilarityStrategy;
import net.ricecode.similarity.StringSimilarityService;
import net.ricecode.similarity.StringSimilarityServiceImpl;

import java.util.ArrayList;
import java.util.Collection;

public class UtilFunctions {
    public static double getStringDistance(String s1, String s2) {
        SimilarityStrategy strategy = new JaroWinklerStrategy();
        StringSimilarityService service = new StringSimilarityServiceImpl(strategy);
        return service.score(s1, s2);
    }

    public static String concatStrArrWithoutBlank(Collection<String> strs){
        StringBuilder builder = new StringBuilder();
        for(String str : strs){
            builder.append(str);
        }

        return builder.toString();
    }
}
