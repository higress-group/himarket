package com.alibaba.himarket.dto.result.httpapi;

import com.alibaba.himarket.dto.converter.OutputConverter;
import com.aliyun.sdk.service.apig20240327.models.Backend;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author zh
 */
@Data
public class BackendResult implements OutputConverter<BackendResult, Backend> {

    private String scene;

    private List<ServiceResult> services;

    @Override
    public BackendResult convertFrom(Backend backend) {
        OutputConverter.super.convertFrom(backend);

        this.services = Optional.ofNullable(backend.getServices())
                .map(serviceList -> serviceList.stream()
                        .map(new ServiceResult()::convertFrom)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());

        return this;
    }
}
