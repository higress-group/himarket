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


package com.alibaba.apiopenplatform.controller;

import com.alibaba.apiopenplatform.dto.result.AuthResult;
import com.alibaba.apiopenplatform.service.OAuth2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zh
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/developers/oauth2")
public class OAuth2Controller {

    private final OAuth2Service oAuth2Service;

    @PostMapping("/token")
    public AuthResult authenticate(@RequestParam("grant_type") String grantType,
                                   @RequestParam("assertion") String assertion) {
        return oAuth2Service.authenticate(grantType, assertion);
    }
}
