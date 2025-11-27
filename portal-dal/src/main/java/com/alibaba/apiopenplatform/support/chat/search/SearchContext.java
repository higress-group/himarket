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

package com.alibaba.apiopenplatform.support.chat.search;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.net.URI;
import java.net.URISyntaxException;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class SearchContext {
    
    private int id;
    
    private String url;
    
    private String title;
    
    private String content;
    
    private String siteName;
    
    private String date;
    
    public void postInit() {
        if ((siteName == null || siteName.isEmpty()) && url != null && !url.isEmpty()) {
            try {
                URI uri = new URI(url);
                this.siteName = uri.getHost();
            } catch (URISyntaxException e) {
                // 日志记录，但不抛出异常
                System.err.println("Failed to parse URL '" + url + "': " + e.getMessage());
            }
        }
    }
    
    public String formatCitation() {
        return String.format("[[citation:%d]] title:%s,content:%s (url: %s, date:%s)", id, title, content, url, date);
    }

}
