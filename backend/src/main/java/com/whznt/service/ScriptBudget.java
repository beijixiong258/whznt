package com.whznt.service;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

record ScriptBudget(int seconds, int target, int minimum, int maximum) {

    static final Pattern CITATION = Pattern.compile("【资料(\\d+)】");
    private static final Pattern HEADING = Pattern.compile("(?m)^ {0,3}#{1,6}(?:\\h+.*)?$");
    private static final Pattern SPOKEN_WORD = Pattern.compile("\\p{IsHan}|[\\p{IsLatin}\\p{N}]+");

    static ScriptBudget forSeconds(int seconds) {
        int target = Math.round(seconds * 220 / 60f);
        return new ScriptBudget(seconds, target, Math.round(target * 0.75f), Math.round(target * 1.15f));
    }

    static int countWords(String text) {
        return (int) SPOKEN_WORD.matcher(bodyText(text)).results().count();
    }

    static String bodyText(String text) {
        return CITATION.matcher(HEADING.matcher(text).replaceAll("")).replaceAll("");
    }

    boolean contains(int words) {
        return words >= minimum && words <= maximum;
    }

    int tokenLimit() {
        return Math.max(700, seconds * 7);
    }

    Map<String, Integer> promptFields() {
        int opening = Math.round(target * 0.15f);
        int closing = Math.round(target * 0.2f);
        return Map.of("时长秒", seconds, "目标字数", target, "最少字数", minimum, "最多字数", maximum,
                "开头参考字数", opening, "正文参考字数", target - opening - closing, "收尾参考字数", closing);
    }

    List<Message> revision(List<Message> messages, String answer, int words) {
        String edit = words > maximum ? "压缩稿件，只留最贴题的一件事和一个实用办法。"
                : "用一个具体的通用做法展开现有观点，增加可直接念出的内容。";
        String correction = """
                上一版实测正文 %d 字。%s 按原始口播规格重写，正文目标 %d 字，范围 %d 至 %d 字。
                直接交付完整三段。已核对的事实保持原意，引用号紧贴对应事实。结尾是实际要念的最后一句。
                """.formatted(words, edit, target, minimum, maximum);
        List<Message> revision = new ArrayList<>(messages);
        revision.add(new AssistantMessage(answer));
        revision.add(new UserMessage(correction));
        return revision;
    }
}
