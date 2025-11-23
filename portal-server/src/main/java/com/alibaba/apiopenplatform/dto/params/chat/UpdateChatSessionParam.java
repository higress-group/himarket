package com.alibaba.apiopenplatform.dto.params.chat;

import com.alibaba.apiopenplatform.dto.converter.InputConverter;
import com.alibaba.apiopenplatform.entity.ChatSession;
import lombok.Data;

/**
 * @author zh
 */
@Data
public class UpdateChatSessionParam implements InputConverter<ChatSession> {

    private String name;
}
