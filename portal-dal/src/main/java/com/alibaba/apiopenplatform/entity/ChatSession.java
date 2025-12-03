package com.alibaba.apiopenplatform.entity;

import com.alibaba.apiopenplatform.converter.ListJsonConverter;
import lombok.Data;
import lombok.experimental.Accessors;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "chat_session", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"session_id"}, name = "uk_session_id")
})
@Data
@Accessors(chain = true)
public class ChatSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Session ID
     */
    @Column(name = "session_id", nullable = false, unique = true, length = 64)
    private String sessionId;

    /**
     * User ID
     */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /**
     * Session name
     */
    @Column(name = "name", length = 255)
    private String name;

    /**
     * Product IDs, support multiple products
     */
    @Column(name = "products", length = 255)
    @Convert(converter = ListJsonConverter.class)
    private List<String> products;

//    /**
//     * Session status
//     */
//    @Column(name = "status", length = 32)
//    @Enumerated(EnumType.STRING)
//    private ChatSessionStatus status = ChatSessionStatus.READY;
}
