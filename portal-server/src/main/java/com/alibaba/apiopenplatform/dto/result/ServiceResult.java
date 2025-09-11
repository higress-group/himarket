package com.alibaba.apiopenplatform.dto.result;


import com.alibaba.apiopenplatform.dto.converter.OutputConverter;
import com.aliyun.sdk.service.apig20240327.models.AiServiceConfig;
import com.aliyun.sdk.service.apig20240327.models.Service;
import lombok.Data;

import java.util.List;

@Data
public class ServiceResult implements OutputConverter<ServiceResult, Service> {

    protected String serviceId;

    protected String serviceName;

    protected AiServiceConfig aiServiceConfig;

    protected String sourceType;

    protected List<String> addresses;

    protected List<Service.Ports> ports;

    @Override
    public ServiceResult convertFrom(Service service) {
        if (service == null) {
            return this;
        }
        this.serviceId = service.getServiceId();
        this.serviceName = service.getName();
        this.aiServiceConfig = service.getAiServiceConfig();
        this.sourceType = service.getSourceType();
        this.addresses = service.getAddresses();
        this.ports = service.getPorts();
        return this;
    }
}
