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

package com.alibaba.himarket.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method or class as publicly accessible without authentication.
 *
 * <p>When applied at the <b>method level</b>, only that specific endpoint is public.
 * When applied at the <b>class level</b>, all endpoints in the controller are public
 * unless overridden by an auth annotation on individual methods.
 *
 * <p><b>Priority rule:</b> Auth annotations ({@code @AdminAuth}, {@code @DeveloperAuth},
 * {@code @AdminOrDeveloperAuth}) always take precedence over {@code @PublicAccess}.
 * If a method carries both {@code @PublicAccess} and an auth annotation, the endpoint
 * will still require authentication.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicAccess {}
