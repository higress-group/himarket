package com.alibaba.apiopenplatform.service.chat.dto;

import com.alibaba.apiopenplatform.dto.converter.OutputConverter;
import com.alibaba.apiopenplatform.entity.Chat;
import com.alibaba.apiopenplatform.support.chat.attachment.AttachmentConfig;
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

    private List<AttachmentConfig> attachments;

    private String answerId;

    private String answer;
}
