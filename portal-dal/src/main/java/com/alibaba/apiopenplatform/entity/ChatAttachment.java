package com.alibaba.apiopenplatform.entity;

import com.alibaba.apiopenplatform.converter.AttachmentDataConverter;
import com.alibaba.apiopenplatform.support.chat.attachment.ChatAttachmentData;
import com.alibaba.apiopenplatform.support.enums.ChatAttachmentType;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.persistence.*;

@Entity
@Table(name = "chat_attachment", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"attachment_id"}, name = "uk_attachment_id")
})
@Data
@Accessors(chain = true)
public class ChatAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Attachment ID
     */
    @Column(name = "attachment_id", nullable = false, unique = true, length = 64)
    private String attachmentId;

    /**
     * User ID
     */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /**
     * Attachment name
     */
    @Column(name = "name", length = 255)
    private String name;

    /**
     * Attachment type, IMAGE/VIDEO/DOCUMENT
     */
    @Column(name = "type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private ChatAttachmentType type;

    /**
     * MIME type
     */
    @Column(name = "mime_type", length = 64)
    private String mimeType;

    /**
     * Size
     */
    @Column(name = "size", nullable = false)
    private Long size;

    /**
     * Data
     */
    @Lob
    @Column(name = "data")
    @Convert(converter = AttachmentDataConverter.class)
    private ChatAttachmentData data;
}