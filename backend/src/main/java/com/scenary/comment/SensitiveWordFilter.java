package com.scenary.comment;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.scenary.config.CommentModerationProperties;

/** 轻量词表过滤：不记录、不回显命中的具体词项。 */
@Component
public class SensitiveWordFilter {

    private final List<String> words;

    public SensitiveWordFilter(CommentModerationProperties properties) {
        String configured = properties.getSensitiveWords();
        words = configured == null ? List.of() : Arrays.stream(configured.split(","))
                .map(String::strip)
                .filter(word -> !word.isEmpty())
                .map(word -> word.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    public boolean contains(String content) {
        String normalized = content.toLowerCase(Locale.ROOT);
        return words.stream().anyMatch(normalized::contains);
    }
}
