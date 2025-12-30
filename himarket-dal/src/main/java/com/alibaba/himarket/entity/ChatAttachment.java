/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.entity;

import com.alibaba.himarket.support.enums.ChatAttachmentType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(
        name = "chat_attachment",
        uniqueConstraints = {
            @UniqueConstraint(
                    columnNames = {"attachment_id"},
                    name = "uk_attachment_id")
        })
@Data
@Accessors(chain = true)
public class ChatAttachment extends BaseEntity {

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
