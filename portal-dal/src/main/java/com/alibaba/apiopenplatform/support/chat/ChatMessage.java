package com.alibaba.apiopenplatform.support.chat;

import lombok.Builder;
import lombok.Data;

/**
 * @author zh
 */
@Data
@Builder
public class ChatMessage {

    private String role;

    private Object content;
}
