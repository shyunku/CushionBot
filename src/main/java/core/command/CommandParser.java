package core.command;

import java.util.ArrayList;
import java.util.Arrays;

public class CommandParser {
    private char startTag;
    private String keyword;
    private ArrayList<String> segments;
    private String rawString;

    public CommandParser(){
    }

    public CommandParser(String rawStr, boolean isServer){
        setThis(rawStr, isServer);
    }

    public void setThis(String rawStr, boolean isServer){
        rawString = rawStr;
        if (isServer) {
            String newPartition = rawStr.substring(getFirstMeaningfulCharIndex(rawStr));
            String[] segmentArray = newPartition.split(" ");
            segments = new ArrayList<String>(Arrays.asList(segmentArray));
            keyword = segments.get(0);
            segments.remove(0);
        }else{
            int starting_index = getFirstMeaningfulCharIndex(rawStr);
            startTag = rawStr.charAt(starting_index);
            String partition = rawStr.substring(starting_index + 1);
            String newPartition = partition.substring(getFirstMeaningfulCharIndex(partition));
            String[] segmentArray = newPartition.split(" ");
            segments = new ArrayList<String>(Arrays.asList(segmentArray));
            keyword = segments.get(0);
            segments.remove(0);
        }
    }

    private int getFirstMeaningfulCharIndex(String str){
        for(int i=0;i<str.length();i++)
            if(str.charAt(i) != ' ') {
                return i;
            }
        return 0;
    }

    public char getStartTag() {
        return startTag;
    }

    public String getKeyword() {
        return keyword;
    }

    public ArrayList<String> getSegments() {
        return segments;
    }

    public String getIntegratedString(){
        StringBuilder str = new StringBuilder();
        for(int i=0;i<segments.size();i++){
            str.append(segments.get(i));
            if(i<segments.size()-1)str.append(' ');
        }

        return str.toString();
    }

    public String getWholeString(){
        if(segments.isEmpty()){
            return keyword;
        }
        return keyword + " " + getIntegratedString();
    }


    public String getRawString(){
        return rawString;
    }
}
