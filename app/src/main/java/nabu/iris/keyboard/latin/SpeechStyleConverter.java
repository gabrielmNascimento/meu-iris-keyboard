/*
 * Copyright (C) 2026 Latin IME Customizer
 */

package nabu.iris.keyboard.latin;

/**
 * Converts Japanese and Korean translations between casual and polite spoken style.
 * Offline ML Kit has no formality setting, so this is applied to the translated text.
 */
final class SpeechStyleConverter {
    static final String STYLE_CASUAL = "casual";
    static final String STYLE_POLITE = "polite";

    private static final int HANGUL_BASE = 0xAC00;
    private static final int JUNG_COUNT = 21;
    private static final int JONG_COUNT = 28;
    private static final int JONG_BIEUP = 17;

    private SpeechStyleConverter() {}

    static String apply(String text, String languageCode, String style) {
        if (text == null || text.isEmpty() || languageCode == null) {
            return text;
        }
        boolean casual = STYLE_CASUAL.equals(style);
        if ("ja".equals(languageCode)) {
            return casual ? japaneseToCasual(text) : japaneseToPolite(text);
        }
        if ("ko".equals(languageCode)) {
            return casual ? koreanToCasual(text) : koreanToPolite(text);
        }
        return text;
    }

    static boolean supportsLanguage(String languageCode) {
        return "ja".equals(languageCode) || "ko".equals(languageCode);
    }

    private static String japaneseToCasual(String text) {
        String result = replaceJapaneseMasuForms(text, true);
        result = replaceAll(result, "ではありません", "ではない");
        result = replaceAll(result, "じゃありません", "じゃない");
        result = replaceAll(result, "じゃないです", "じゃない");
        result = replaceAll(result, "してください", "して");
        result = replaceAll(result, "ください", "くれ");
        result = replaceAll(result, "でした", "だった");
        result = replaceAll(result, "ですよね", "だよね");
        result = replaceAll(result, "ですね", "だね");
        result = replaceAll(result, "ですよ", "だよ");
        result = replaceAll(result, "ですか", "か");
        result = replaceAll(result, "ですけど", "だけど");
        result = replaceAll(result, "ですので", "なので");
        result = replaceAll(result, "ですが", "だが");
        result = replaceAll(result, "ですし", "だし");
        result = replaceAll(result, "です", "だ");
        return result;
    }

    private static String japaneseToPolite(String text) {
        String result = replaceAll(text, "だった", "でした");
        result = replaceAll(result, "だよね", "ですよね");
        result = replaceAll(result, "だね", "ですね");
        result = replaceAll(result, "だよ", "ですよ");
        result = replaceAll(result, "だけど", "ですけど");
        result = replaceAll(result, "だが", "ですが");
        result = replaceAll(result, "だし", "ですし");
        result = replaceJapaneseCopulaDa(result);
        return result;
    }

    private static String replaceJapaneseCopulaDa(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 'だ') {
                boolean end = (i + 1 >= text.length())
                        || isJapaneseClauseEnd(text.charAt(i + 1));
                boolean notDatta = i + 1 >= text.length() || text.charAt(i + 1) != 'っ';
                if (end && notDatta) {
                    sb.append("です");
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String replaceJapaneseMasuForms(String text, boolean toCasual) {
        if (!toCasual) {
            return text;
        }
        String[] suffixes = {
                "ませんでした", "ませんか", "ません", "ましたか", "ました",
                "ましょう", "ますか", "ますね", "ますよ", "ます"
        };
        String result = text;
        for (String suffix : suffixes) {
            result = replaceMasuSuffix(result, suffix);
        }
        return result;
    }

    private static String replaceMasuSuffix(String text, String suffix) {
        StringBuilder sb = new StringBuilder();
        int from = 0;
        int index;
        while ((index = text.indexOf(suffix, from)) >= 0) {
            int stemStart = findJapaneseStemStart(text, index);
            sb.append(text, from, stemStart);
            String stem = text.substring(stemStart, index);
            String converted = convertMasuStem(stem, suffix);
            sb.append(converted != null ? converted : text.substring(stemStart, index + suffix.length()));
            from = index + suffix.length();
        }
        sb.append(text, from, text.length());
        return sb.toString();
    }

    private static int findJapaneseStemStart(String text, int suffixStart) {
        int i = suffixStart - 1;
        while (i >= 0 && isJapaneseWordChar(text.charAt(i))) {
            char c = text.charAt(i);
            if (isJapaneseParticle(c) && i + 1 < suffixStart) {
                return i + 1;
            }
            i--;
        }
        return i + 1;
    }

    private static String convertMasuStem(String stem, String suffix) {
        if (stem.isEmpty()) {
            return null;
        }
        String irregular = convertIrregularMasu(stem, suffix);
        if (irregular != null) {
            return irregular;
        }
        char last = stem.charAt(stem.length() - 1);
        String base = stem.substring(0, stem.length() - 1);
        boolean past = suffix.startsWith("ました");
        boolean negPast = suffix.startsWith("ませんでした");
        boolean neg = suffix.startsWith("ません");
        boolean volitional = suffix.startsWith("ましょう");
        boolean question = suffix.endsWith("か");
        boolean ne = suffix.endsWith("ね");
        boolean yo = suffix.endsWith("よ");

        String converted;
        if (isERowKana(last) || isKanji(last)) {
            if (negPast) {
                converted = stem + "なかった";
            } else if (neg) {
                converted = stem + "ない";
            } else if (past) {
                converted = stem + "た";
            } else if (volitional) {
                converted = stem + "よう";
            } else {
                converted = stem + "る";
            }
        } else if (isIRowKana(last)) {
            if ("行き".equals(stem) && (past || negPast)) {
                converted = negPast ? "行かなかった" : "行った";
            } else if (negPast) {
                String aRow = iRowToARow(last);
                if (aRow == null) {
                    return null;
                }
                converted = base + aRow + "なかった";
            } else if (neg) {
                String aRow = iRowToARow(last);
                if (aRow == null) {
                    return null;
                }
                converted = base + aRow + "ない";
            } else if (past) {
                String ta = iRowToTa(last);
                if (ta == null) {
                    return null;
                }
                converted = base + ta;
            } else if (volitional) {
                String oRow = iRowToORow(last);
                if (oRow == null) {
                    return null;
                }
                converted = base + oRow + "う";
            } else {
                String uRow = iRowToURow(last);
                if (uRow == null) {
                    return null;
                }
                converted = base + uRow;
            }
        } else {
            return null;
        }

        if (question) {
            converted += "か";
        } else if (ne) {
            converted += "ね";
        } else if (yo) {
            converted += "よ";
        }
        return converted;
    }

    private static String convertIrregularMasu(String stem, String suffix) {
        boolean past = suffix.startsWith("ました");
        boolean negPast = suffix.startsWith("ませんでした");
        boolean neg = suffix.startsWith("ません");
        boolean volitional = suffix.startsWith("ましょう");
        boolean question = suffix.endsWith("か");
        boolean ne = suffix.endsWith("ね");
        boolean yo = suffix.endsWith("よ");

        String core = null;
        if ("し".equals(stem)) {
            if (negPast) core = "しなかった";
            else if (neg) core = "しない";
            else if (past) core = "した";
            else if (volitional) core = "しよう";
            else core = "する";
        } else if ("来".equals(stem) || "き".equals(stem)) {
            if (negPast) core = "来".equals(stem) ? "来なかった" : "こなかった";
            else if (neg) core = "来".equals(stem) ? "来ない" : "こない";
            else if (past) core = "来".equals(stem) ? "来た" : "きた";
            else if (volitional) core = "来".equals(stem) ? "来よう" : "こよう";
            else core = "来".equals(stem) ? "来る" : "くる";
        } else if ("あり".equals(stem)) {
            if (negPast) core = "なかった";
            else if (neg) core = "ない";
            else if (past) core = "あった";
            else if (volitional) core = "あろう";
            else core = "ある";
        } else if ("い".equals(stem)) {
            if (negPast) core = "いなかった";
            else if (neg) core = "いない";
            else if (past) core = "いた";
            else if (volitional) core = "いよう";
            else core = "いる";
        } else if ("でき".equals(stem)) {
            if (negPast) core = "できなかった";
            else if (neg) core = "できない";
            else if (past) core = "できた";
            else if (volitional) core = "できよう";
            else core = "できる";
        }
        if (core == null) {
            return null;
        }
        if (question) core += "か";
        else if (ne) core += "ね";
        else if (yo) core += "よ";
        return core;
    }

    private static boolean isJapaneseWordChar(char c) {
        return isKanji(c) || (c >= 0x3040 && c <= 0x309F) || (c >= 0x30A0 && c <= 0x30FF);
    }

    private static boolean isKanji(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF);
    }

    private static boolean isJapaneseParticle(char c) {
        // Omit で so verbs like できます keep their full stem.
        return c == 'は' || c == 'が' || c == 'を' || c == 'に'
                || c == 'と' || c == 'も' || c == 'の' || c == 'へ' || c == 'や';
    }

    private static boolean isJapaneseClauseEnd(char c) {
        return c == '。' || c == '！' || c == '？' || c == '!' || c == '?'
                || c == '\n' || c == ' ' || c == '　' || c == '、';
    }

    private static boolean isERowKana(char c) {
        return "えけげせてでねへべぺめれ".indexOf(c) >= 0;
    }

    private static boolean isIRowKana(char c) {
        return "いきぎしじちぢにひびぴみり".indexOf(c) >= 0;
    }

    private static String iRowToURow(char c) {
        switch (c) {
            case 'い': return "う";
            case 'き': return "く";
            case 'ぎ': return "ぐ";
            case 'し': return "す";
            case 'じ': return "ず";
            case 'ち': return "つ";
            case 'に': return "ぬ";
            case 'ひ': return "ふ";
            case 'び': return "ぶ";
            case 'ぴ': return "ぷ";
            case 'み': return "む";
            case 'り': return "る";
            default: return null;
        }
    }

    private static String iRowToARow(char c) {
        switch (c) {
            case 'い': return "わ";
            case 'き': return "か";
            case 'ぎ': return "が";
            case 'し': return "さ";
            case 'じ': return "ざ";
            case 'ち': return "た";
            case 'に': return "な";
            case 'ひ': return "は";
            case 'び': return "ば";
            case 'ぴ': return "ぱ";
            case 'み': return "ま";
            case 'り': return "ら";
            default: return null;
        }
    }

    private static String iRowToORow(char c) {
        switch (c) {
            case 'い': return "お";
            case 'き': return "こ";
            case 'ぎ': return "ご";
            case 'し': return "そ";
            case 'じ': return "ぞ";
            case 'ち': return "と";
            case 'に': return "の";
            case 'ひ': return "ほ";
            case 'び': return "ぼ";
            case 'ぴ': return "ぽ";
            case 'み': return "も";
            case 'り': return "ろ";
            default: return null;
        }
    }

    private static String iRowToTa(char c) {
        switch (c) {
            case 'き': return "いた";
            case 'ぎ': return "いだ";
            case 'し': return "した";
            case 'い':
            case 'ち':
            case 'り': return "った";
            case 'に':
            case 'び':
            case 'み': return "んだ";
            default: return null;
        }
    }

    private static String koreanToCasual(String text) {
        String result = replaceAll(text, "안녕하세요", "안녕");
        result = replaceAll(result, "감사합니다", "감사해");
        result = replaceAll(result, "고맙습니다", "고마워");
        result = replaceAll(result, "죄송합니다", "미안해");
        result = replaceAll(result, "미안합니다", "미안해");
        result = replaceAll(result, "잘 부탁드립니다", "잘 부탁해");
        result = replaceAll(result, "그렇습니다", "그래");
        result = replaceAll(result, "하세요", "해");
        result = replaceAll(result, "입니다", "이야");
        result = replaceAll(result, "입니까", "이야");
        result = replaceAll(result, "이에요", "이야");
        result = replaceAll(result, "예요", "야");
        result = replaceAll(result, "에요", "야");
        result = replaceSeumnida(result);
        result = replaceAll(result, "합니다", "해");
        result = replaceAll(result, "합니까", "해");
        result = stripKoreanYo(result);
        return result;
    }

    private static String koreanToPolite(String text) {
        String result = replaceAtClauseEnd(text, "이야", "이에요");
        result = replaceAtClauseEnd(result, "야", "예요");
        result = appendKoreanYo(result);
        return result;
    }

    private static String replaceSeumnida(String text) {
        String[] suffixes = {"습니까", "습니다"};
        String result = text;
        for (String suffix : suffixes) {
            StringBuilder sb = new StringBuilder();
            int from = 0;
            int index;
            while ((index = result.indexOf(suffix, from)) >= 0) {
                if (index == 0 || !isHangul(result.charAt(index - 1))) {
                    sb.append(result, from, index + suffix.length());
                    from = index + suffix.length();
                    continue;
                }
                char last = result.charAt(index - 1);
                sb.append(result, from, index - 1);
                String spoken = toKoreanCasualEnding(last);
                if ("습니까".equals(suffix)) {
                    spoken += "?";
                }
                sb.append(spoken);
                from = index + suffix.length();
            }
            sb.append(result, from, result.length());
            result = sb.toString();
        }
        return replaceBinida(result);
    }

    private static String replaceBinida(String text) {
        String[] suffixes = {"니까", "니다"};
        String result = text;
        for (String suffix : suffixes) {
            StringBuilder sb = new StringBuilder();
            int from = 0;
            int index;
            while ((index = result.indexOf(suffix, from)) >= 0) {
                if (index == 0 || !isHangul(result.charAt(index - 1))) {
                    sb.append(result, from, index + suffix.length());
                    from = index + suffix.length();
                    continue;
                }
                char last = result.charAt(index - 1);
                if (jong(last) != JONG_BIEUP) {
                    sb.append(result, from, index + suffix.length());
                    from = index + suffix.length();
                    continue;
                }
                char stem = compose(cho(last), jung(last), 0);
                sb.append(result, from, index - 1);
                String spoken = toKoreanCasualEnding(stem);
                if ("니까".equals(suffix)) {
                    spoken += "?";
                }
                sb.append(spoken);
                from = index + suffix.length();
            }
            sb.append(result, from, result.length());
            result = sb.toString();
        }
        return result;
    }

    private static String toKoreanCasualEnding(char stem) {
        if (stem == '하') {
            return "해";
        }
        if (isBieupIrregular(stem)) {
            char withoutB = compose(cho(stem), jung(stem), 0);
            return withoutB + "워";
        }
        String ending = isBrightVowel(jung(stem)) ? "아" : "어";
        if (jong(stem) == 0) {
            if (jung(stem) == 0 && "아".equals(ending)) {
                return String.valueOf(stem);
            }
            if (jung(stem) == 4 && "어".equals(ending)) {
                return String.valueOf(stem);
            }
            if (stem == '하') {
                return "해";
            }
        }
        return stem + ending;
    }

    private static boolean isBieupIrregular(char stem) {
        return jong(stem) == JONG_BIEUP && "갑겁곱눕덥돕렵맵밉쉽웁춥".indexOf(stem) >= 0;
    }

    private static String stripKoreanYo(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '요' && isKoreanYoEnding(text, i)) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean isKoreanYoEnding(String text, int index) {
        if (index == 0) {
            return false;
        }
        char prev = text.charAt(index - 1);
        if (!isHangul(prev)) {
            return false;
        }
        if (index + 1 < text.length()) {
            char next = text.charAt(index + 1);
            if (isHangul(next) || Character.isLetterOrDigit(next)) {
                return false;
            }
        }
        return true;
    }

    private static String appendKoreanYo(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(c);
            if (!isHangul(c)) {
                continue;
            }
            boolean boundary = (i + 1 >= text.length()) || isKoreanClauseEnd(text.charAt(i + 1));
            if (!boundary) {
                continue;
            }
            if (c == '요' || (i > 0 && text.charAt(i) == '요')) {
                continue;
            }
            if (looksLikeKoreanCasualEnding(text, i)) {
                sb.append('요');
            }
        }
        return sb.toString();
    }

    private static boolean looksLikeKoreanCasualEnding(String text, int index) {
        char c = text.charAt(index);
        if (c == '나' || c == '너' || c == '저' || c == '이' || c == '그') {
            return false;
        }
        return "해야어아여워와봐가서줘돼네게".indexOf(c) >= 0;
    }

    private static boolean isKoreanClauseEnd(char c) {
        return c == '.' || c == '!' || c == '?' || c == '\n' || c == ' '
                || c == ',' || c == '~' || c == '…';
    }

    private static boolean isBrightVowel(int jung) {
        return jung == 0 || jung == 2 || jung == 8 || jung == 12;
    }

    private static boolean isHangul(char c) {
        return c >= 0xAC00 && c <= 0xD7A3;
    }

    private static int cho(char c) {
        return (c - HANGUL_BASE) / (JUNG_COUNT * JONG_COUNT);
    }

    private static int jung(char c) {
        return ((c - HANGUL_BASE) / JONG_COUNT) % JUNG_COUNT;
    }

    private static int jong(char c) {
        return (c - HANGUL_BASE) % JONG_COUNT;
    }

    private static char compose(int cho, int jung, int jong) {
        return (char) (HANGUL_BASE + (cho * JUNG_COUNT + jung) * JONG_COUNT + jong);
    }

    private static String replaceAll(String text, String from, String to) {
        if (!text.contains(from)) {
            return text;
        }
        return text.replace(from, to);
    }

    private static String replaceAtClauseEnd(String text, String from, String to) {
        if (!text.contains(from)) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        int fromIndex = 0;
        int index;
        while ((index = text.indexOf(from, fromIndex)) >= 0) {
            int after = index + from.length();
            boolean atEnd = after >= text.length() || isKoreanClauseEnd(text.charAt(after));
            sb.append(text, fromIndex, index);
            sb.append(atEnd ? to : from);
            fromIndex = after;
        }
        sb.append(text, fromIndex, text.length());
        return sb.toString();
    }
}
