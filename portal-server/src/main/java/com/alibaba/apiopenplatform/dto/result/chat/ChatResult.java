package com.alibaba.apiopenplatform.dto.result.chat;

import com.alibaba.apiopenplatform.dto.converter.OutputConverter;
import com.alibaba.apiopenplatform.entity.Chat;
import com.alibaba.apiopenplatform.support.chat.attachment.ChatAttachmentConfig;
import com.alibaba.apiopenplatform.support.enums.ChatStatus;
import lombok.Data;

import java.util.List;

/**
 * @author zh
 */

@Data
public class ChatResult implements OutputConverter<ChatResult, Chat> {

    private String chatId;

    private String sessionId;

    private String conversationId;

    private ChatStatus status;

    private String productId;

    private String questionId;

    private String question;

    private List<ChatAttachmentConfig> attachments;

    private String answerId;

    private String answer;
}
