package com.alibaba.apiopenplatform.dto.params.chat;

import com.alibaba.apiopenplatform.dto.converter.InputConverter;
import com.alibaba.apiopenplatform.entity.ChatSession;
import com.alibaba.apiopenplatform.support.enums.TalkType;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class CreateChatSessionParam implements InputConverter<ChatSession> {

    /**
     * Products to use
     */
    @NotEmpty(message = "products cannot be empty")
    private List<String> products;

    /**
     * Model or Agent
     */
    @NotNull(message = "talkType cannot be null")
    private TalkType talkType;

    /**
     * Session name
     */
    @NotBlank(message = "name cannot be empty")
    private String name;
}
