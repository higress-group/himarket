package com.alibaba.apiopenplatform.support.chat.attachment;

import com.alibaba.apiopenplatform.support.enums.ChatAttachmentType;
import lombok.Data;

/**
 * @author zh
 */
@Data
public class ChatAttachmentConfig {

    private ChatAttachmentType type;

    private String attachmentId;
}
