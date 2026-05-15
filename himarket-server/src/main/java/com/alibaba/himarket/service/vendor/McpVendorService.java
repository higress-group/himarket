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
import com.alibaba.himarket.dto.vendor.RemoteMcpItemResult;
import com.alibaba.himarket.support.enums.McpVendorType;

/** MCP 供应商业务服务，提供远程 MCP 列表查询能力。 */
public interface McpVendorService {

    /** 查询远程 MCP 列表（带已存在标记）。 */
    PageResult<RemoteMcpItemResult> listRemoteMcpItems(
            McpVendorType vendorType, String keyword, int page, int size);
}
