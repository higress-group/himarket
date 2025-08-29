package com.alibaba.apiopenplatform.converter;

import com.alibaba.apiopenplatform.support.gateway.AdpAIGatewayConfig;

import javax.persistence.Converter;

@Converter(autoApply = true)
public class AdpAIGatewayConfigConverter extends JsonConverter<AdpAIGatewayConfig> {

    public AdpAIGatewayConfigConverter() {
        super(AdpAIGatewayConfig.class);
    }
}
