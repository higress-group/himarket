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
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Component
public class PublicAccessPathScanner implements ApplicationContextAware {

    private String[] publicAccessPaths = new String[0];

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        try {
            List<String> paths = new ArrayList<>();
            String[] beanNames = applicationContext.getBeanNamesForAnnotation(RestController.class);
            for (String beanName : beanNames) {
                scanController(applicationContext.getType(beanName), paths);
            }
            String[] controllerBeanNames =
                    applicationContext.getBeanNamesForAnnotation(Controller.class);
            for (String beanName : controllerBeanNames) {
                Class<?> beanType = applicationContext.getType(beanName);
                if (beanType != null
                        && !AnnotatedElementUtils.hasAnnotation(beanType, RestController.class)) {
                    scanController(beanType, paths);
                }
            }
            publicAccessPaths = paths.toArray(new String[0]);
            if (publicAccessPaths.length > 0) {
                log.info(
                        "Discovered {} @PublicAccess paths: {}",
                        publicAccessPaths.length,
                        List.of(publicAccessPaths));
            }
        } catch (Exception e) {
            log.warn("Failed to scan @PublicAccess paths, no public paths will be registered", e);
        }
    }

    public String[] getPublicAccessPaths() {
        return publicAccessPaths;
    }

    private void scanController(Class<?> controllerClass, List<String> paths) {
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
            boolean hasAuthAnnotation = hasAuthAnnotation(method);
            boolean methodLevelPublic =
                    AnnotatedElementUtils.hasAnnotation(method, PublicAccess.class);

            // Auth annotation takes priority over @PublicAccess
            if (hasAuthAnnotation) {
                continue;
            }

            if (methodLevelPublic || classLevelPublic) {
                String[] methodPaths = getMethodLevelPaths(method);
                for (String classPath : classLevelPaths) {
                    for (String methodPath : methodPaths) {
                        paths.add(normalizePath(classPath + methodPath));
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

    private boolean hasRequestMapping(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class);
    }

    private boolean hasAuthAnnotation(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, AdminAuth.class)
                || AnnotatedElementUtils.hasAnnotation(method, DeveloperAuth.class)
                || AnnotatedElementUtils.hasAnnotation(method, AdminOrDeveloperAuth.class);
    }

    private String normalizePath(String path) {
        if (path.isEmpty()) {
            return "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        // Replace path variables like {productId} with *
        path = path.replaceAll("\\{[^}]+}", "*");
        // Handle Spring's {*filePath} catch-all pattern → **
        path = path.replace("/**/", "/**/");
        return path;
    }
}
