package dev.unyielding.quests;

/** Colour-code helpers usable on both sides. */
public final class Text {
    private Text() {
    }

    /** Converts "&6" colour codes into Minecraft section codes. */
    public static String codes(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(s.charAt(i + 1)) >= 0) {
                sb.append('§');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
