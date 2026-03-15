package com.alibaba.himarket.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "coding_session",
        uniqueConstraints = {
            @UniqueConstraint(
                    columnNames = {"session_id"},
                    name = "uk_session_id")
        })
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodingSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true, length = 64)
    private String sessionId;

    @Column(name = "cli_session_id", nullable = false, length = 128)
    private String cliSessionId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "provider_key", length = 64)
    private String providerKey;

    @Column(name = "cwd", length = 512)
    private String cwd;

    @Column(name = "model_product_id", length = 64)
    private String modelProductId;

    @Column(name = "model_name", length = 128)
    private String modelName;
}
