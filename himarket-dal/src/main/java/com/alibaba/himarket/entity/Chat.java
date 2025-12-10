package com.alibaba.himarket.entity;

import com.alibaba.himarket.converter.ChatUsageConverter;
import com.alibaba.himarket.converter.ListJsonConverter;
import com.alibaba.himarket.support.chat.ChatUsage;
import com.alibaba.himarket.support.chat.attachment.ChatAttachmentConfig;
import com.alibaba.himarket.support.enums.ChatStatus;
import jakarta.persistence.*;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Entity
@Table(
        name = "chat",
        uniqueConstraints = {
            @UniqueConstraint(
                    columnNames = {"chat_id"},
                    name = "uk_chat_id")
        })
@Data
@Accessors(chain = true)
public class Chat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Chat ID */
    @Column(name = "chat_id", nullable = false, unique = true, length = 64)
    private String chatId;

    /** Session ID */
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    /** User ID */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** Conversation ID (Chat group ID) */
    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    /** Chat status: INIT/PROCESSING/SUCCESS/FAILED */
    @Column(name = "status", length = 32)
    @Enumerated(EnumType.STRING)
    private ChatStatus status = ChatStatus.INIT;

    /** Product ID */
    @Column(name = "product_id", length = 64)
    private String productId;

    /** Question ID */
    @Column(name = "question_id", length = 64)
    private String questionId;

    /** Question */
    @Column(name = "question", columnDefinition = "text")
    private String question;

    /** Multi-modal content */
    @Column(name = "attachments", columnDefinition = "json")
    @Convert(converter = ListJsonConverter.class)
    private List<ChatAttachmentConfig> attachments;

    /** Answer ID */
    @Column(name = "answer_id", length = 64)
    private String answerId;

    /** Answer from product */
    @Column(name = "answer", columnDefinition = "longtext")
    private String answer;

    /** The index of the question submitted */
    @Column(name = "sequence", columnDefinition = "int DEFAULT 0")
    private Integer sequence;

    /** Usage */
    @Column(name = "chat_usage", columnDefinition = "json")
    @Convert(converter = ChatUsageConverter.class)
    private ChatUsage chatUsage;
}
