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

package com.alibaba.apiopenplatform.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.core.security.ContextHolder;
import com.alibaba.apiopenplatform.support.chat.search.SearchContext;
import com.alibaba.apiopenplatform.support.chat.search.SearchInput;
import com.alibaba.apiopenplatform.service.TalkSearchAbilityService;
import com.alibaba.apiopenplatform.service.PortalService;
import com.alibaba.apiopenplatform.support.chat.search.SearchOutput;
import com.alibaba.apiopenplatform.support.enums.SearchEngineType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Service
@Slf4j
public class TalkSearchAbilityServiceGoogleImpl
        implements TalkSearchAbilityService<Map<String, Object>, ResponseEntity<String>> {
    
    private final RestTemplate restTemplate;
    private final PortalService portalService;
    private final ContextHolder contextHolder;
    
    private static final String GOOGLE_SEARCH_URL = "https://serpapi.com/search";
    
    private static final String GOOGLE_SEARCH_TYPE = "GOOGLE";
    
    public static final List<String> STANDARD_RESULT_TYPES = Arrays.asList(
            "news_results",
            "organic_results",
            "related_questions"
    );
    
    public static final String KNOWLEDGE_CARD_SNIPPET =
            "Description: %s\n" +
                    "Details:\n" +
                    "%s";
    
    public static final Set<String> KNOWLEDGE_GRAPH_IGNORE_FIELDS = new HashSet<>(Arrays.asList(
            "title",
            "description",
            "knowledge_graph_search_link",
            "serpapi_knowledge_graph_search_link",
            "image",
            "Kgmid",
            "Entity_Type"
    ));
    
    public static final List<String> LINK_CANDIDATES = Arrays.asList(
            "knowledge_graph_search_link",
            "serpapi_knowledge_graph_search_link",
            "website"
    );
    
    public TalkSearchAbilityServiceGoogleImpl(RestTemplate restTemplate,
                                   PortalService portalService,
                                   ContextHolder contextHolder) {
        this.restTemplate = restTemplate;
        this.portalService = portalService;
        this.contextHolder = contextHolder;
    }
    
    /**
     * 获取 Google 搜索 API Key
     * 从当前 Portal 的配置中动态获取（自动解密）
     * 
     * @return API Key
     */
    private String getSearchApiKey() {
        String portalId = contextHolder.getPortal(); // 获取当前 Portal ID
        return portalService.getSearchEngineApiKey(portalId, SearchEngineType.GOOGLE);
    }
    
    @Override
    public List<SearchContext> search(SearchInput ideaTalkSearchInput) {
        Map<String,Object> param = buildSearchRequest(ideaTalkSearchInput);
        try{
            // 使用UriComponentsBuilder构建URL，避免双重编码问题
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(GOOGLE_SEARCH_URL);
            for (Map.Entry<String, Object> entry : param.entrySet()) {
                builder.queryParam(entry.getKey(), entry.getValue());
            }

            // 关键：使用 encode() 进行正确的一次性编码，避免双重编码
            URI uri = builder.build().encode().toUri();
            
            log.debug("Request URL: {}", uri.toString());

            // 创建请求头，指定application/json
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);

            // 使用exchange方法并添加请求头，直接传递URI对象
            ResponseEntity<String> searchResponse = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return buildSearchResponse(searchResponse);
        }catch (Exception e){
            log.error("TalkSearchAbilityServiceGoogleImpl search error:{}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public Map<String, Object> buildSearchRequest(SearchInput ideaTalkSearchInput) {
        Map<String,Object> param = new HashMap<>(6);
        param.put("q",ideaTalkSearchInput.getQuery());
        param.put("api_key",getSearchApiKey());
        param.put("engine","google");
        param.put("google_domain","google.com");
        param.put("num",10);
        
        // 添加时间范围参数
        String tbs = buildTimeRangeParameter(ideaTalkSearchInput.getTime());
        if (tbs != null) {
            param.put("tbs", tbs);
        }
        
        return param;
    }
    
    /**
     * 构建时间范围参数
     * @param time 时间列表，格式: ["2025-03-31", "2025-04-30"]
     * @return tbs参数，格式: "cdr:1,cd_min:03/31/2025,cd_max:04/30/2025"，如果time为空则返回null
     */
    private String buildTimeRangeParameter(List<String> time) {
        if (time == null || time.isEmpty()) {
            return null;
        }
        
        if (time.size() != 2) {
            log.warn("Time parameter should contain exactly 2 elements, got {}", time.size());
            return null;
        }
        
        try {
            String startDate = convertDateFormat(time.get(0));
            String endDate = convertDateFormat(time.get(1));
            return String.format("cdr:1,cd_min:%s,cd_max:%s", startDate, endDate);
        } catch (Exception e) {
            log.error("Failed to build time range parameter from time: {}", time, e);
            return null;
        }
    }
    
    /**
     * 转换日期格式从 "2025-03-31" 到 "03/31/2025"
     * @param date 日期字符串，格式: "YYYY-MM-DD"
     * @return 转换后的日期字符串，格式: "MM/DD/YYYY"
     */
    private String convertDateFormat(String date) {
        if (date == null || date.isEmpty()) {
            throw new IllegalArgumentException("Date cannot be null or empty");
        }
        
        String[] parts = date.split("-");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid date format: " + date + ", expected YYYY-MM-DD");
        }
        
        return String.format("%s/%s/%s", parts[1], parts[2], parts[0]);
    }
    
    @Override
    public String getSearchType() {
        return GOOGLE_SEARCH_TYPE;
    }
    
    @Override
    public List<SearchContext> buildSearchResponse(ResponseEntity<String> searchResponse) {
        List<SearchContext> searchContexts = new ArrayList<>();
        int id = 1;
        if (searchResponse.getStatusCode().is2xxSuccessful()){
            String body = searchResponse.getBody();
            JSONObject jsonObject = JSONUtil.parseObj(body);
            
            for(String resultType: STANDARD_RESULT_TYPES) {
                List<Map<String, Object>> resultList = getResultList(jsonObject, resultType);
                for (Map<String, Object> result : resultList) {
                   String snippet = getStringValue(result, "snippet", "");
                   if (snippet.isEmpty()){
                       continue;
                   }
                   
                   if("related_question".equals(resultType)){
                       String question = getStringValue(result, "question", "");
                       if(!question.isEmpty() && !snippet.isEmpty()){
                           snippet = String.format("Ask:%s --> Answer:%s", question, snippet);
                       }
                   }
                   
                   SearchContext searchContext = new SearchContext()
                           .setUrl(getStringValue(result, "link", ""))
                           .setTitle(getStringValue(result, "title", ""))
                           .setContent( snippet)
                           .setSiteName(getStringValue(result, "source", ""))
                           .setDate(getStringValue(result, "date", ""));
                   searchContext.setId(id++);
                   searchContext.postInit();
                   
                   searchContexts.add(searchContext);
                }
            }
            
            // 解析知识图谱
            Map<String, Object> knowledgeGraph = getMapValue(jsonObject, "knowledge_graph");
            if (knowledgeGraph != null && !knowledgeGraph.isEmpty()) {
                SearchContext searchContext= parseKnowledgeGraph(knowledgeGraph);
                searchContext.setId(id++);
                searchContexts.add(searchContext);
            }
            
            return searchContexts;
        }
        
        return searchContexts;
    }
    
    private List<Map<String, Object>> getResultList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            try {
                return (List<Map<String, Object>>) value;
            } catch (ClassCastException e) {
                log.warn("Failed to cast {} to List<Map>", key);
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }
    
    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }
    
    private Map<String, Object> getMapValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            try {
                return (Map<String, Object>) value;
            } catch (ClassCastException e) {
                log.warn("Failed to cast {} to Map", key);
                return null;
            }
        }
        return null;
    }
    
    private SearchContext parseKnowledgeGraph(Map<String, Object> knowledgeGraph) {
        // 提取详细信息
        List<String> details = new ArrayList<>();
        for (Map.Entry<String, Object> entry : knowledgeGraph.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (isValidKnowledgeField(key, value)) {
                String capitalizedKey = capitalizeFirst(key);
                details.add(String.format("- %s: %s", capitalizedKey, value.toString()));
            }
        }
        
        // 格式化 snippet
        String description = getStringValue(knowledgeGraph, "description", "").trim();
        String detailsStr = String.join("\n", details);
        String snippet = String.format(
                KNOWLEDGE_CARD_SNIPPET,
                description,
                detailsStr
        ).trim();
        
        // 查找链接
        String link = "";
        for (String candidate : LINK_CANDIDATES) {
            link = getStringValue(knowledgeGraph, candidate, "");
            if (!link.isEmpty()) {
                break;
            }
        }
        
        // 获取来源名称
        String siteName = "";
        Map<String, Object> source = getMapValue(knowledgeGraph, "source");
        if (source != null) {
            siteName = getStringValue(source, "name", "");
        }
        
        
        
        SearchContext searchContext = new SearchContext()
                .setUrl(link)
                .setTitle(getStringValue(knowledgeGraph, "title", ""))
                .setSiteName(siteName)
                .setContent(snippet)
                .setDate(getStringValue(knowledgeGraph, "date", ""));
        
        searchContext.postInit();
        return searchContext;
    }
    
    /**
     * 检查知识图谱字段是否有效
     */
    private boolean isValidKnowledgeField(String key, Object value) {
        // 检查类型
        if (!(value instanceof String) || key == null) {
            return false;
        }
        
        // 检查是否在忽略列表中
        if (KNOWLEDGE_GRAPH_IGNORE_FIELDS.contains(key)) {
            return false;
        }
        
        // 检查是否以 _link 或 _stick 结尾
        String lowerKey = key.toLowerCase();
        if (lowerKey.endsWith("_link") || lowerKey.endsWith("_stick")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 首字母大写
     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
