package com.alibaba.apiopenplatform.service;

import com.alibaba.apiopenplatform.dto.params.sls.GenericSlsQueryRequest;
import com.alibaba.apiopenplatform.dto.params.sls.GenericSlsQueryResponse;
import com.alibaba.apiopenplatform.dto.params.sls.SlsCheckLogstoreRequest;
import com.alibaba.apiopenplatform.dto.params.sls.SlsCheckProjectRequest;
import com.alibaba.apiopenplatform.dto.params.sls.SlsCommonQueryRequest;

/**
 * 通用SLS日志查询服务
 * 支持多种认证方式和场景化查询
 *
 */
public interface SlsLogService {

    /**
     * 执行通用SQL查询
     *
     * @param request 查询请求
     * @return 查询结果
     */
    GenericSlsQueryResponse executeQuery(GenericSlsQueryRequest request);

    /**
     * 执行通用SQL查询
     *
     * @param request 查询请求
     * @return 查询结果
     */
    GenericSlsQueryResponse executeQuery(SlsCommonQueryRequest request);


    /**
     * 检查Project是否存在
     *
     * @param request 查询请求（包含认证信息）
     * @return 是否存在
     */
    Boolean checkProjectExists(SlsCheckProjectRequest request);

    /**
     * 检查Logstore是否存在
     *
     * @param request 查询请求（包含认证信息）
     * @return 是否存在
     */
    Boolean checkLogstoreExists(SlsCheckLogstoreRequest request);

    /**
     * 为全局日志的 logstore 更新索引
     * 使用配置中心的 project 和 logstore
     *
     * @param userId 用户ID（用于STS认证）
     */
    void updateLogIndex(String userId);

}
