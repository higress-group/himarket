package com.alibaba.apiopenplatform.service.chat.dto;

import com.alibaba.apiopenplatform.dto.result.model.ModelConfigResult;
import com.alibaba.apiopenplatform.support.chat.ChatMessage;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @author zh
 */
@Data
@Builder
public class InvokeModelParam {

    private ModelConfigResult modelConfig;

    private Map<String, String> requestHeaders;

    private List<ChatMessage> chatMessages;

    private Boolean stream;
}
