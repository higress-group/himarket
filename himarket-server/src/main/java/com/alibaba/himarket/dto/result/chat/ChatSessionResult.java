package com.alibaba.himarket.dto.result.chat;

import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.entity.ChatSession;
import com.alibaba.himarket.support.enums.TalkType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author zh
 */
@Data
public class ChatSessionResult implements OutputConverter<ChatSessionResult, ChatSession> {

    private String sessionId;

    private String name;

    private TalkType talkType;

    private List<String> products;

    private LocalDateTime createAt;

    private LocalDateTime updateAt;
}
