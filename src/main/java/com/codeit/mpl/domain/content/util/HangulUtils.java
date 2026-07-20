package com.codeit.mpl.domain.content.util;

public class HangulUtils {
    private static final char HANGUL_BEGIN = 0xAC00;
    private static final char HANGUL_END = 0xD7A3;
    private static final char[] CHOSUNG = {
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ',
        'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    public static String extractChosung(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= HANGUL_BEGIN && c <= HANGUL_END) {
                int chosungIndex = (c - HANGUL_BEGIN) / (21 * 28);
                sb.append(CHOSUNG[chosungIndex]);
            } else if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
