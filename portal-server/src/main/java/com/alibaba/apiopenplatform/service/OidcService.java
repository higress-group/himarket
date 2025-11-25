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


package com.alibaba.apiopenplatform.service;

import com.alibaba.apiopenplatform.dto.result.common.AuthResult;
import com.alibaba.apiopenplatform.dto.result.idp.IdpResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

public interface OidcService {

    /**
     * 重定向到授权服务器
     *
     * @param provider
     * @param apiPrefix
     * @param request
     * @return
     */
    String buildAuthorizationUrl(String provider, String apiPrefix, HttpServletRequest request);

    /**
     * 授权服务器回调
     *
     * @param code
     * @param state
     * @param request
     * @param response
     * @return
     */
    AuthResult handleCallback(String code, String state, HttpServletRequest request, HttpServletResponse response);

    /**
     * 可用的OIDC认证列表
     *
     * @return
     */
    List<IdpResult> getAvailableProviders();

}
