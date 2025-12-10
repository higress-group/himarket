package com.alibaba.himarket.converter;

import com.alibaba.himarket.support.chat.ChatUsage;
import jakarta.persistence.Converter;

/**
 * @author zh
 */
@Converter(autoApply = true)
public class ChatUsageConverter extends JsonConverter<ChatUsage> {

    protected ChatUsageConverter() {
        super(ChatUsage.class);
    }
}
