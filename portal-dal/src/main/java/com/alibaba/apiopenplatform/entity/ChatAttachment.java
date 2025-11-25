package com.alibaba.apiopenplatform.entity;

import com.alibaba.apiopenplatform.support.enums.ChatAttachmentType;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.*;

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
    @Column(name = "size", columnDefinition = "bigint")
    @ColumnDefault("0")
    private Long size;

    /**
     * Raw data
     */
    @Column(name = "data", columnDefinition = "mediumblob")
    private byte[] data;
}