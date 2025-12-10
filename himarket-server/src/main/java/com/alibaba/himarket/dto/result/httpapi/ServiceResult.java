package com.alibaba.himarket.dto.result.httpapi;

import com.alibaba.himarket.dto.converter.OutputConverter;
import com.aliyun.sdk.service.apig20240327.models.Backend.Services;
import lombok.Data;

/**
 * @author zh
 */
@Data
public class ServiceResult implements OutputConverter<ServiceResult, Services> {

    private String name;

    private Integer port;

    private String protocol;

    private Integer weight;
}
