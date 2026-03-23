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

package com.alibaba.himarket.config;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "observability")
public class ObservabilityConfig {

    private final SlsConfig slsConfig;
    private final DataSource dataSource;

    @PostConstruct
    public void init() {
        log.info("Observability log source: {}", logSource);
        if (logSource == LogSource.SLS) {
            log.info(
                    "SLS endpoint: {}, project: {}, logstore: {}, authType: {}",
                    slsConfig.getEndpoint(),
                    slsConfig.getDefaultProject(),
                    slsConfig.getDefaultLogstore(),
                    slsConfig.getAuthType());
            if (!slsConfig.isConfigured()) {
                log.warn("SLS endpoint is not configured! Queries will return empty results.");
            }
        } else {
            try {
                String url = dataSource.getConnection().getMetaData().getURL();
                log.info("DB datasource URL: {}, table: access_logs", url);
            } catch (Exception e) {
                log.info("DB datasource: unable to retrieve URL ({})", e.getMessage());
            }
        }
    }

    public enum LogSource {
        DB,
        SLS
    }

    /**
     * 日志数据源：SLS（默认，查询阿里云日志服务）或 DB（查询本地 access_logs 表）
     */
    private LogSource logSource = LogSource.SLS;
}
