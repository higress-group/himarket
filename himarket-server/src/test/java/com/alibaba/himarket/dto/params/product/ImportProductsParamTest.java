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

package com.alibaba.himarket.dto.params.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.alibaba.himarket.support.enums.ProductImportSource;
import com.alibaba.himarket.support.enums.ProductType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ImportProductsParamTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeExternalSourceConfigByImportSource() throws Exception {
        String json =
                """
                {
                  "items": [
                    {
                      "description": "Fetch web content",
                      "resourceId": "@modelcontextprotocol/fetch",
                      "resourceName": "Fetch"
                    }
                  ],
                  "productType": "MCP_SERVER",
                  "source": "EXTERNAL",
                  "sourceConfig": {
                    "provider": "MODELSCOPE"
                  }
                }
                """;

        ImportProductsParam param = objectMapper.readValue(json, ImportProductsParam.class);

        assertEquals(ProductImportSource.EXTERNAL, param.getSource());
        assertEquals(ProductType.MCP_SERVER, param.getProductType());
        ExternalImportConfigParam sourceConfig =
                assertInstanceOf(ExternalImportConfigParam.class, param.getSourceConfig());
        assertEquals("MODELSCOPE", sourceConfig.getProvider());
    }
}
