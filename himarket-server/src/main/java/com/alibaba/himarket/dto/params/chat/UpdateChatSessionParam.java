package com.alibaba.himarket.dto.params.chat;

import com.alibaba.himarket.dto.converter.InputConverter;
import com.alibaba.himarket.entity.ChatSession;
import lombok.Data;

/**
 * @author zh
 */
@Data
public class UpdateChatSessionParam implements InputConverter<ChatSession> {

    private String name;
}
