package com.whznt.service;

import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class StoryGrounding {

    private static final String ASSERTION = "(?:没有|没先|从未|从不|不曾|反复|屡次|一再)[^，,：:。！？；【】“”\\r\\n]{1,32}";
    private static final Pattern CONTRAST = Pattern.compile("(" + ASSERTION + ")[，,]\\h*(?:而是|只是|只)");
    private static final Pattern MARKED_CLAIM = Pattern.compile(ASSERTION);
    private static final Pattern SENTENCE = Pattern.compile("[^。！？；]+[。！？；]*(?:【资料\\d+】)*|[。！？；]+");

    private StoryGrounding() {
    }

    static String retainSupportedClaims(String story, List<String> evidence) {
        List<String> normalized = evidence.stream().map(StoryGrounding::normalize).toList();
        // A citation alone does not establish an unreported omission or frequency.
        return story.lines().map(line -> {
            if (line.startsWith("#") || line.isBlank()) return line;
            return SENTENCE.matcher(line).results().map(MatchResult::group)
                    .map(sentence -> CONTRAST.matcher(sentence).replaceAll(contrast ->
                            supported(normalized, contrast.group(1)) ? Matcher.quoteReplacement(contrast.group()) : ""))
                    .filter(sentence -> MARKED_CLAIM.matcher(sentence).results()
                            .allMatch(claim -> supported(normalized, claim.group())))
                    .collect(Collectors.joining());
        }).collect(Collectors.joining("\n")).strip();
    }

    private static boolean supported(List<String> evidence, String claim) {
        String target = normalize(claim);
        return evidence.stream().anyMatch(source -> source.contains(target));
    }

    private static String normalize(String value) {
        return value.replaceAll("[\\p{P}\\p{Z}\\s]+", "");
    }
}
