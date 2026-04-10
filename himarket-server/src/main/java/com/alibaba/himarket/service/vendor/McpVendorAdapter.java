/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.service.vendor;

import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.vendor.RemoteMcpItem;
import com.alibaba.himarket.support.enums.McpVendorType;

/** 供应商适配器接口，封装不同供应商的 API 差异，对外暴露统一的查询接口。 */
public interface McpVendorAdapter {

    /** 适配器对应的供应商类型。 */
    McpVendorType getType();

    /** 分页查询供应商 MCP 列表，支持关键词搜索；认证由各适配器内部自动处理。 */
    PageResult<RemoteMcpItem> listMcpServers(String keyword, int page, int size);

    /**
     * 导入前补充详情：调用供应商详情 API 获取完整的连接配置、额外参数等信息。
     *
     * <p>列表接口通常只返回基本信息（名称、描述等），导入时需要通过详情接口获取
     * server_config / connectionConfig / extraParams 等完整数据。
     *
     * <p>默认实现直接返回原始 item（适用于列表接口已包含完整信息的供应商）。
     */
    default RemoteMcpItem enrichForImport(RemoteMcpItem item) {
        return item;
    }
}
