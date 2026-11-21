package com.whznt.service;

import com.whznt.exception.WorkspaceException;
import reactor.core.publisher.Flux;

import java.time.Duration;

final class GeneratedContent {

    private static final int MAX_LENGTH = 30_000;

    private GeneratedContent() {
    }

    static StringBuilder append(StringBuilder text, String chunk) {
        if (chunk.length() > MAX_LENGTH - text.length()) {
            throw WorkspaceException.badRequest("模型输出超过 30,000 个字符，请缩短要求后重试。");
        }
        return text.append(chunk);
    }

    static String collect(Flux<String> chunks, Duration timeout) {
        return chunks.reduceWith(StringBuilder::new, GeneratedContent::append)
                .map(StringBuilder::toString).blockOptional(timeout).orElse("");
    }
}
