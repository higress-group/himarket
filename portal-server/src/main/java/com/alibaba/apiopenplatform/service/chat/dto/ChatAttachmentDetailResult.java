package com.alibaba.apiopenplatform.service.chat.dto;

import com.alibaba.apiopenplatform.dto.converter.OutputConverter;
import com.alibaba.apiopenplatform.entity.ChatAttachment;
import com.alibaba.apiopenplatform.support.chat.attachment.ChatAttachmentData;
import com.alibaba.apiopenplatform.support.enums.ChatAttachmentType;
import lombok.Data;

/**
 * @author zh
 */
@Data
public class ChatAttachmentDetailResult implements OutputConverter<ChatAttachmentDetailResult, ChatAttachment> {

    private String attachmentId;

    private ChatAttachmentType type;

    private String mimeType;

    private Long size;

    private ChatAttachmentData data;
}
