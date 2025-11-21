package com.alibaba.apiopenplatform.service.chat.service;

import com.alibaba.apiopenplatform.entity.ChatAttachment;
import com.alibaba.apiopenplatform.repository.ChatAttachmentRepository;
import com.alibaba.apiopenplatform.service.chat.dto.ChatAttachmentDetailResult;
import com.alibaba.apiopenplatform.support.chat.attachment.ChatAttachmentData;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author zh
 */
@Service
@AllArgsConstructor
public class ChatAttachmentService {

    private final ChatAttachmentRepository chatAttachmentRepository;

    public ChatAttachmentData getAttachmentData(String attachmentId) {
        return chatAttachmentRepository.findByAttachmentId(attachmentId)
                .map(ChatAttachment::getData)
                .orElse(null);
    }

    public ChatAttachmentDetailResult getAttachmentDetail(String attachmentId) {
        return chatAttachmentRepository.findByAttachmentId(attachmentId)
                .map(a -> new ChatAttachmentDetailResult().convertFrom(a))
                .orElse(null);
    }
}
