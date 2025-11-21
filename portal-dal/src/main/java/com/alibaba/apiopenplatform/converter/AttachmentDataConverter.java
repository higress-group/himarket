package com.alibaba.apiopenplatform.converter;

import com.alibaba.apiopenplatform.support.chat.attachment.ChatAttachmentData;

import javax.persistence.Converter;

@Converter(autoApply = true)
public class AttachmentDataConverter extends JsonConverter<ChatAttachmentData> {
    public AttachmentDataConverter() {
        super(ChatAttachmentData.class);
    }
}
