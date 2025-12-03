package com.alibaba.apiopenplatform.support.chat.content;

import com.alibaba.apiopenplatform.support.enums.ContentType;
import lombok.Data;

/**
 * @author zh
 */
@Data
public class TextContent extends MessageContent {

    private String text;

    public TextContent(String text) {
        super.type = ContentType.TEXT.getType();
        this.text = text;
    }
}
