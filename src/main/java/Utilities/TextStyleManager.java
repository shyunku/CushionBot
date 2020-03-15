package Utilities;

public class TextStyleManager {
    public String toBold(String str){
        return wrap(str, "**");
    }

    public String toBlock(String str){
        return wrap(str, "`");
    }

    public String toBox(String str){
        return wrap(str, "```");
    }

    public String toItalic(String str){
        return wrap(str, "*");
    }

    public String toUnderlined(String str){
        return wrap(str, "__");
    }

    public String wrap(String str, String wrapper){
        return wrapper + str + wrapper;
    }
}
