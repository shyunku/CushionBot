package Utilities;

import net.dv8tion.jda.api.entities.Member;

public class TextStyler {
    public static String Bold(String str) {
        return wrap(str, "**");
    }

    public static String Block(String str) {
        return wrap(str, "`");
    }

    public static String Blockf(String fmt, Object... args) {
        return Block(String.format(fmt, args));
    }

    public static String Link(String label, String url) {
        return String.format("[%s](%s)", label, url);
    }

    public static String Link(String url) {
        return String.format("[%s](%s)", url, url);
    }

    public static String Box(String str) {
        return wrap(str, "```");
    }

    public static String Italic(String str) {
        return wrap(str, "*");
    }

    public static String Underline(String str) {
        return wrap(str, "__");
    }

    public static String wrap(String str, String wrapper) {
        return wrapper + str + wrapper;
    }

    public static String member(Member member) {
        return String.format("<@%s>", member.getId());
    }
}
