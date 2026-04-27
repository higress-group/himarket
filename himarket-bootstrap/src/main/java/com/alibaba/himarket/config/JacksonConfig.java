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

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.springframework.context.annotation.Configuration;

/**
 * Global Jackson configuration.
 *
 * <p>Overrides the default {@link StreamReadConstraints} to allow deserializing large JSON strings.
 * This is necessary because the Nacos SDK uses its own internal {@code ObjectMapper} (via {@code
 * JacksonUtils}) which inherits the JVM-wide default constraints. Without this override, Skill
 * resources larger than 20 MB will fail to deserialize with a {@code
 * StreamConstraintsException}.
 *
 * <p>The limit is set to 50 MB (50,000,000 chars) instead of unlimited, which is sufficient for
 * the maximum Skill ZIP size (10 MB) after decompression and base64 encoding expansion (~1.33x),
 * while still providing a safety net against excessively large payloads.
 *
 * @see <a href="https://github.com/higress-group/himarket/issues/279">Issue #279</a>
 */
@Configuration
public class JacksonConfig {

    /**
     * 50 MB in characters — sufficient for 10 MB ZIP decompressed content with base64 overhead.
     */
    private static final int MAX_STRING_LENGTH = 50_000_000;

    static {
        StreamReadConstraints constraints =
                StreamReadConstraints.builder()
                        .maxStringLength(MAX_STRING_LENGTH)
                        .build();
        StreamReadConstraints.overrideDefaultStreamReadConstraints(constraints);
    }
}
