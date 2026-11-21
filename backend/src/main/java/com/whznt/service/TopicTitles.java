package com.whznt.service;

import java.util.List;
import java.util.regex.Pattern;

public final class TopicTitles {

    private static final Pattern HEADING = Pattern.compile("(?m)^#{2,3}[ \t]+(.+)$");
    private static final Pattern LIST = Pattern.compile("(?m)^[ \t]*(?:[-*+][ \t]+|\\d+[.、）)][ \t]*)(.+)$");

    private TopicTitles() {
    }

    public static List<String> parse(String content) {
        List<String> headings = matches(HEADING, content);
        if (!headings.isEmpty()) {
            return headings;
        }
        List<String> entries = matches(LIST, content);
        if (!entries.isEmpty()) {
            return entries;
        }
        return content.lines().map(String::strip)
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(TopicTitles::title).toList();
    }

    private static List<String> matches(Pattern pattern, String content) {
        return pattern.matcher(content).results()
                .map(match -> title(match.group(1))).filter(line -> !line.isEmpty()).toList();
    }

    private static String title(String line) {
        String text = line.strip().replaceFirst("^\\d+[.、）)]\\s*", "");
        int count = text.codePointCount(0, text.length());
        return count <= 300 ? text : text.substring(0, text.offsetByCodePoints(0, 300));
    }
}
