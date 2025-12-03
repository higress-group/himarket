package com.alibaba.apiopenplatform.dto.result.chat;

import com.alibaba.apiopenplatform.dto.converter.OutputConverter;
import com.alibaba.apiopenplatform.entity.ChatSession;
import com.alibaba.apiopenplatform.support.enums.TalkType;
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
