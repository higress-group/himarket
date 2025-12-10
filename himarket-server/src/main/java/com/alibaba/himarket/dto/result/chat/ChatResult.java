package com.alibaba.himarket.dto.result.chat;

import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.entity.Chat;
import com.alibaba.himarket.support.chat.attachment.ChatAttachmentConfig;
import com.alibaba.himarket.support.enums.ChatStatus;
import java.util.List;
import lombok.Data;

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
