package com.alibaba.himarket.repository;

import com.alibaba.himarket.entity.ChatAttachment;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * @author zh
 */
@Repository
public interface ChatAttachmentRepository extends BaseRepository<ChatAttachment, Long> {

    Optional<ChatAttachment> findByAttachmentId(String attachmentId);

    List<ChatAttachment> findByAttachmentIdIn(List<String> attachmentIds);
}
