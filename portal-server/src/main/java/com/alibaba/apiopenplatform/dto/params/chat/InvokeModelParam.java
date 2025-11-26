package com.alibaba.apiopenplatform.dto.params.chat;

import com.alibaba.apiopenplatform.dto.result.model.ModelConfigResult;
import com.alibaba.apiopenplatform.dto.result.product.ProductResult;
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

    private ProductResult product;

    private Map<String, String> requestHeaders;

    private Map<String, String> queryParams;

    private List<ChatMessage> chatMessages;

    private Boolean stream;

    private List<String> gatewayIps;
}
