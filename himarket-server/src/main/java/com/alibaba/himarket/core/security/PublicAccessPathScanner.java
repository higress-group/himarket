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

package com.alibaba.himarket.core.security;

import com.alibaba.himarket.core.annotation.AdminAuth;
import com.alibaba.himarket.core.annotation.AdminOrDeveloperAuth;
import com.alibaba.himarket.core.annotation.DeveloperAuth;
import com.alibaba.himarket.core.annotation.PublicAccess;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Component
public class PublicAccessPathScanner implements ApplicationContextAware {

    /** Represents a public endpoint with its HTTP method and path pattern. */
    public record PublicAccessEndpoint(HttpMethod httpMethod, String path) {}

    private Set<PublicAccessEndpoint> publicAccessEndpoints = Set.of();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        try {
            Set<PublicAccessEndpoint> endpoints = new HashSet<>();
            String[] beanNames = applicationContext.getBeanNamesForAnnotation(RestController.class);
            for (String beanName : beanNames) {
                scanController(applicationContext.getType(beanName), endpoints);
            }
            String[] controllerBeanNames =
                    applicationContext.getBeanNamesForAnnotation(Controller.class);
            for (String beanName : controllerBeanNames) {
                Class<?> beanType = applicationContext.getType(beanName);
                if (beanType != null
                        && !AnnotatedElementUtils.hasAnnotation(beanType, RestController.class)) {
                    scanController(beanType, endpoints);
                }
            }
            publicAccessEndpoints = endpoints;
            if (!publicAccessEndpoints.isEmpty()) {
                log.info(
                        "Discovered {} @PublicAccess endpoints: {}",
                        publicAccessEndpoints.size(),
                        publicAccessEndpoints);
            }
        } catch (Exception e) {
            log.warn("Failed to scan @PublicAccess paths, no public paths will be registered", e);
        }
    }

    /** Returns the list of public access endpoints with HTTP method information. */
    public List<PublicAccessEndpoint> getPublicAccessEndpoints() {
        return List.copyOf(publicAccessEndpoints);
    }

    /** Returns the public access paths (for backward compatibility). */
    public String[] getPublicAccessPaths() {
        return publicAccessEndpoints.stream()
                .map(PublicAccessEndpoint::path)
                .distinct()
                .toArray(String[]::new);
    }

    private void scanController(Class<?> controllerClass, Set<PublicAccessEndpoint> endpoints) {
        if (controllerClass == null) {
            return;
        }
        String[] classLevelPaths = getClassLevelPaths(controllerClass);
        boolean classLevelPublic =
                AnnotatedElementUtils.hasAnnotation(controllerClass, PublicAccess.class);

        for (Method method : controllerClass.getDeclaredMethods()) {
            if (!hasRequestMapping(method)) {
                continue;
            }
            boolean hasAuthAnnotation = hasAuthAnnotation(method, controllerClass);
            boolean methodLevelPublic =
                    AnnotatedElementUtils.hasAnnotation(method, PublicAccess.class);

            // Auth annotation takes priority over @PublicAccess
            if (hasAuthAnnotation) {
                continue;
            }

            if (methodLevelPublic || classLevelPublic) {
                HttpMethod[] httpMethods = getHttpMethods(method);
                String[] methodPaths = getMethodLevelPaths(method);
                for (String classPath : classLevelPaths) {
                    for (String methodPath : methodPaths) {
                        String normalizedPath = normalizePath(classPath, methodPath);
                        for (HttpMethod httpMethod : httpMethods) {
                            endpoints.add(new PublicAccessEndpoint(httpMethod, normalizedPath));
                        }
                    }
                }
            }
        }
    }

    private String[] getClassLevelPaths(Class<?> controllerClass) {
        RequestMapping classMapping =
                AnnotatedElementUtils.findMergedAnnotation(controllerClass, RequestMapping.class);
        if (classMapping != null && classMapping.value().length > 0) {
            return classMapping.value();
        }
        return new String[] {""};
    }

    private String[] getMethodLevelPaths(Method method) {
        RequestMapping methodMapping =
                AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (methodMapping != null && methodMapping.value().length > 0) {
            return methodMapping.value();
        }
        return new String[] {""};
    }

    private HttpMethod[] getHttpMethods(Method method) {
        RequestMapping methodMapping =
                AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (methodMapping != null && methodMapping.method().length > 0) {
            return java.util.Arrays.stream(methodMapping.method())
                    .map(rm -> HttpMethod.valueOf(rm.name()))
                    .toArray(HttpMethod[]::new);
        }
        // No specific method means all methods; use null to indicate "any"
        return new HttpMethod[] {null};
    }

    private boolean hasRequestMapping(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class);
    }

    private boolean hasAuthAnnotation(Method method, Class<?> controllerClass) {
        // Check method-level auth annotations
        boolean methodHasAuth =
                AnnotatedElementUtils.hasAnnotation(method, AdminAuth.class)
                        || AnnotatedElementUtils.hasAnnotation(method, DeveloperAuth.class)
                        || AnnotatedElementUtils.hasAnnotation(method, AdminOrDeveloperAuth.class);
        if (methodHasAuth) {
            return true;
        }
        // Check class-level auth annotations
        return AnnotatedElementUtils.hasAnnotation(controllerClass, AdminAuth.class)
                || AnnotatedElementUtils.hasAnnotation(controllerClass, DeveloperAuth.class)
                || AnnotatedElementUtils.hasAnnotation(controllerClass, AdminOrDeveloperAuth.class);
    }

    private String normalizePath(String classPath, String methodPath) {
        String path = classPath + methodPath;
        if (path.isEmpty()) {
            return "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        // Replace path variables like {productId} with *
        path = path.replaceAll("\\{[^}]+}", "*");
        return path;
    }
}
