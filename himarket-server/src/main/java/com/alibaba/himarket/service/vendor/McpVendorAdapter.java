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
import com.alibaba.himarket.support.api.spec.McpConnection;
import com.alibaba.himarket.support.enums.McpVendorType;

public interface McpVendorAdapter {

    /**
     * Return the vendor type handled by this adapter.
     *
     * @return the supported vendor type
     */
    McpVendorType getType();

    /**
     * List remote MCP servers with pagination and optional keyword search.
     *
     * @param keyword the optional keyword to search by
     * @param page the page number
     * @param size the page size
     * @return a page of remote MCP server items
     */
    PageResult<RemoteMcpItem> listMcpServers(String keyword, int page, int size);

    /**
     * Enrich a listed MCP item before import by loading provider-specific detail data.
     *
     * <p>List APIs often return only basic fields. Importing may require detail data such as
     * server_config, connectionConfig, or extraParams.
     *
     * <p>The default implementation returns the item unchanged for vendors whose list API already
     * includes all required import data.
     *
     * @param item the listed remote MCP item
     * @return the enriched remote MCP item
     */
    default RemoteMcpItem enrichForImport(RemoteMcpItem item) {
        return item;
    }

    /**
     * Convert provider-specific connection configuration into the platform-standard MCP connection
     * model.
     *
     * <p>Each vendor owns its connectionConfig shape, so implementations must parse their own
     * format and return a standard {@link McpConnection}.
     *
     * @param item the remote MCP item with provider-specific connection data
     * @return the standard MCP connection
     */
    McpConnection buildConnection(RemoteMcpItem item);

    /**
     * Fetch the import-ready MCP detail by provider-side resource identifier.
     *
     * <p>The returned item should include all data required for import, at least mcpName,
     * protocolType, and either connection or a connectionConfig that can build one.
     *
     * @param resourceId the provider-side resource identifier
     * @return the import-ready remote MCP item
     */
    RemoteMcpItem getMcpServer(String resourceId);
}
