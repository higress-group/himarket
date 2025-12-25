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

package com.alibaba.himarket.support.portal;

import java.util.Map;
import lombok.Data;

@Data
public class IdentityMapping {

    /**
     * Field mapping for Developer ID
     */
    private String userIdField;

    /**
     * Field mapping for Developer Name
     */
    private String userNameField;

    /**
     * Field mapping for Developer Email
     */
    private String emailField;

    /**
     * Custom field mappings (reserved for extension)
     */
    private Map<String, String> customFields;
}
