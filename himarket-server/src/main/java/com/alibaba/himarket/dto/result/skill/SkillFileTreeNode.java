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

package com.alibaba.himarket.dto.result.skill;

import java.util.List;
import lombok.Data;

@Data
public class SkillFileTreeNode {

    /** File or directory name */
    private String name;

    /** Relative path from skill root */
    private String path;

    /** Node type: "file" or "directory" */
    private String type;

    /** Encoding of the file content: "text" or "base64" (only for file nodes) */
    private String encoding;

    /** File size in bytes (only for file nodes) */
    private Integer size;

    /** Child nodes (only for directory nodes) */
    private List<SkillFileTreeNode> children;
}
