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

package com.alibaba.himarket.dto.params.developer;

import com.alibaba.himarket.dto.converter.InputConverter;
import com.alibaba.himarket.entity.Developer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateDeveloperParam implements InputConverter<Developer> {

    @Size(max = 64, message = "Username cannot exceed 64 characters")
    private String username;

    @Email(message = "Invalid email format")
    @Size(max = 128, message = "Email cannot exceed 128 characters")
    private String email;

    @Size(max = 256, message = "Avatar URL cannot exceed 256 characters")
    private String avatarUrl;
}
