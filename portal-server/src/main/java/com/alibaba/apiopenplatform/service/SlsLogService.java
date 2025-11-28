package com.aliyun.csb.service;

import com.aliyun.csb.model.entity.K8sClusterConfig;
import com.aliyun.csb.model.request.sls.GenericSlsQueryRequest;
import com.aliyun.csb.model.request.sls.SlsCheckLogstoreRequest;
import com.aliyun.csb.model.request.sls.SlsCheckProjectRequest;
import com.aliyun.csb.model.request.sls.SlsCommonQueryRequest;
import com.aliyun.csb.model.response.sls.GenericSlsQueryResponse;

/**
 * 通用SLS日志查询服务
 * 支持多种认证方式和场景化查询
 *
 * @author jingfeng.xjf
 * @date 2025/11/08
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
    Boolean checkProjectExists(GenericSlsQueryRequest request);

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
    Boolean checkLogstoreExists(GenericSlsQueryRequest request);

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
    void updateGlobalLogIndex(String userId);

    /**
     * 为指定集群确保 AliyunLogConfig CR 存在
     * 统一管理日志收集配置的创建和维护
     *
     * @param cluster K8s 集群配置
     */
    void ensureAliyunLogConfigExists(K8sClusterConfig cluster);
}
