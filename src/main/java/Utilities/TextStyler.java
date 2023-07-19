package Utilities;

public class TextStyler {
    public static String Bold(String str){
        return wrap(str, "**");
    }

    public static String Block(String str){
        return wrap(str, "`");
    }

    public static String Link(String label, String url){
        return String.format("[%s](%s)",label, url);
    }

    public static String Link(String url){
        return String.format("[%s](%s)", url, url);
    }

    public static String Box(String str){
        return wrap(str, "```");
    }

    public static String Italic(String str){
        return wrap(str, "*");
    }

    public static String Underline(String str){
        return wrap(str, "__");
    }

    public static String wrap(String str, String wrapper){
        return wrapper + str + wrapper;
    }
}
