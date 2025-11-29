package com.alibaba.apiopenplatform.dto.params.sls;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * SLS检查Logstore是否存在请求
 *
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SlsCheckLogstoreRequest {

    /**
     * 用户ID（仅当配置为STS认证时需要）
     * 此字段由Controller层从上下文获取并设置，前端不应传入
     */
    @JsonIgnore
    private String userId;

    /**
     * Project名称（可选，不传则使用默认Project）
     */
    private String project;

    /**
     * Logstore名称（可选，不传则检查默认Logstore）
     */
    private String logstore;
}
