package com.alibaba.apiopenplatform.repository;

import com.alibaba.apiopenplatform.entity.ChatAttachment;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * @author zh
 */
@Repository
public interface ChatAttachmentRepository extends BaseRepository<ChatAttachment, Long> {

    Optional<ChatAttachment> findByAttachmentId(String attachmentId);
}