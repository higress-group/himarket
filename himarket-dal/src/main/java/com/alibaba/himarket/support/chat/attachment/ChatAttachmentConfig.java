package com.alibaba.himarket.support.chat.attachment;

import com.alibaba.himarket.support.enums.ChatAttachmentType;
import lombok.Data;

/**
 * @author zh
 */
@Data
public class ChatAttachmentConfig {

    private ChatAttachmentType type;

    private String attachmentId;
}
