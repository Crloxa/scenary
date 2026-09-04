package com.scenary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 基础评论词表；完整审核状态机仍属于 P13。 */
@Component
@ConfigurationProperties(prefix = "scenary.comment")
public class CommentModerationProperties {

    private String sensitiveWords = "诈骗,赌博,色情";

    public String getSensitiveWords() {
        return sensitiveWords;
    }

    public void setSensitiveWords(String sensitiveWords) {
        this.sensitiveWords = sensitiveWords;
    }
}
