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

import com.alibaba.himarket.core.annotation.AdminAuth;
import com.alibaba.himarket.core.annotation.AdminOrDeveloperAuth;
import com.alibaba.himarket.core.annotation.DeveloperAuth;
import com.alibaba.himarket.core.response.Response;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Configuration
public class SwaggerConfig {

    private static final String APPLICATION_JSON =
            org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
    private static final String BEARER_AUTH = "bearerAuth";
    private static final String OPEN_API_KEY = "openApiKey";
    private static final String ERROR_RESPONSE_SCHEMA = "HiMarketErrorResponse";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .addServersItem(new Server().url("http://localhost:8080").description("Local"))
                .info(
                        new Info()
                                .title("HiMarket API")
                                .version("1.0.0")
                                .description("HiMarket platform API documentation"))
                .components(
                        new Components()
                                .addSecuritySchemes(BEARER_AUTH, bearerAuthScheme())
                                .addSecuritySchemes(OPEN_API_KEY, openApiKeyScheme())
                                .addSchemas(ERROR_RESPONSE_SCHEMA, errorResponseSchema())
                                .addResponses("BadRequest", errorResponse("Bad request"))
                                .addResponses("Unauthorized", errorResponse("Unauthorized"))
                                .addResponses("Forbidden", errorResponse("Forbidden"))
                                .addResponses("NotFound", errorResponse("Resource not found"))
                                .addResponses("Conflict", errorResponse("Resource conflict"))
                                .addResponses(
                                        "InternalServerError",
                                        errorResponse("Internal server error")));
    }

    @Bean
    public OperationCustomizer operationCustomizer() {
        return (operation, handlerMethod) -> {
            AuthRequirement authRequirement = resolveAuthRequirement(handlerMethod);
            applySecurity(operation, authRequirement);
            applySuccessResponseWrapper(operation, handlerMethod);
            applyCommonErrorResponses(operation, authRequirement);
            return operation;
        };
    }

    private void applySecurity(Operation operation, AuthRequirement authRequirement) {
        if (authRequirement.openApiKey()) {
            operation.addSecurityItem(new SecurityRequirement().addList(OPEN_API_KEY));
            operation.addExtension("x-auth-type", "API_KEY");
            return;
        }

        if (!authRequirement.roles().isEmpty()) {
            operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
            operation.addExtension("x-roles", authRequirement.roles());
        }
    }

    private void applySuccessResponseWrapper(Operation operation, HandlerMethod handlerMethod) {
        if (shouldKeepRawResponse(handlerMethod)) {
            return;
        }

        ApiResponses responses = ensureResponses(operation);
        ApiResponse success = responses.get("200");
        if (success == null) {
            success = new ApiResponse().description("Success");
            responses.addApiResponse("200", success);
        }

        Schema<?> dataSchema = extractJsonSchema(success);
        if (isVoidReturn(handlerMethod.getMethod())) {
            dataSchema = nullableSchema();
        }
        success.description("Success").content(jsonContent(successResponseSchema(dataSchema)));
    }

    private void applyCommonErrorResponses(Operation operation, AuthRequirement authRequirement) {
        ApiResponses responses = ensureResponses(operation);
        addResponseIfAbsent(responses, "400", "BadRequest");
        addResponseIfAbsent(responses, "404", "NotFound");
        addResponseIfAbsent(responses, "409", "Conflict");
        addResponseIfAbsent(responses, "500", "InternalServerError");
        if (authRequirement.requiresAuthentication()) {
            addResponseIfAbsent(responses, "401", "Unauthorized");
            addResponseIfAbsent(responses, "403", "Forbidden");
        }
    }

    private ApiResponses ensureResponses(Operation operation) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        return operation.getResponses();
    }

    private Schema<?> extractJsonSchema(ApiResponse response) {
        if (response == null || response.getContent() == null) {
            return null;
        }
        io.swagger.v3.oas.models.media.MediaType mediaType =
                response.getContent().get(APPLICATION_JSON);
        return mediaType == null ? null : mediaType.getSchema();
    }

    private boolean shouldKeepRawResponse(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        Class<?> returnType = method.getReturnType();
        return ResponseEntity.class.isAssignableFrom(returnType)
                || Response.class.isAssignableFrom(returnType)
                || SseEmitter.class.isAssignableFrom(returnType)
                || Flux.class.isAssignableFrom(returnType)
                || Mono.class.isAssignableFrom(returnType)
                || Arrays.stream(method.getParameterTypes())
                        .anyMatch(HttpServletResponse.class::isAssignableFrom);
    }

    private boolean isVoidReturn(Method method) {
        return Void.TYPE.equals(method.getReturnType())
                || Void.class.equals(method.getReturnType());
    }

    private AuthRequirement resolveAuthRequirement(HandlerMethod handlerMethod) {
        if (isOpenApiEndpoint(handlerMethod)) {
            return AuthRequirement.withOpenApiKey();
        }

        Set<String> roles = new LinkedHashSet<>();
        addRoleIfAnnotated(roles, handlerMethod, AdminAuth.class, "ADMIN");
        addRoleIfAnnotated(roles, handlerMethod, DeveloperAuth.class, "DEVELOPER");
        if (hasAnnotation(handlerMethod, AdminOrDeveloperAuth.class)) {
            roles.add("ADMIN");
            roles.add("DEVELOPER");
        }
        return AuthRequirement.roles(List.copyOf(roles));
    }

    private <A extends java.lang.annotation.Annotation> void addRoleIfAnnotated(
            Set<String> roles, HandlerMethod handlerMethod, Class<A> annotationType, String role) {
        if (hasAnnotation(handlerMethod, annotationType)) {
            roles.add(role);
        }
    }

    private <A extends java.lang.annotation.Annotation> boolean hasAnnotation(
            HandlerMethod handlerMethod, Class<A> annotationType) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), annotationType)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), annotationType);
    }

    private boolean isOpenApiEndpoint(HandlerMethod handlerMethod) {
        RequestMapping requestMapping =
                AnnotatedElementUtils.findMergedAnnotation(
                        handlerMethod.getBeanType(), RequestMapping.class);
        if (requestMapping == null) {
            return false;
        }
        return Arrays.stream(paths(requestMapping)).anyMatch(path -> path.startsWith("/open-api"));
    }

    private String[] paths(RequestMapping requestMapping) {
        if (requestMapping.path().length > 0) {
            return requestMapping.path();
        }
        if (requestMapping.value().length > 0) {
            return requestMapping.value();
        }
        return new String[] {""};
    }

    private void addResponseIfAbsent(ApiResponses responses, String code, String responseRef) {
        if (!responses.containsKey(code)) {
            responses.addApiResponse(
                    code, new ApiResponse().$ref("#/components/responses/" + responseRef));
        }
    }

    private SecurityScheme bearerAuthScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");
    }

    private SecurityScheme openApiKeyScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-API-Key");
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(
                        jsonContent(
                                new Schema<>()
                                        .$ref("#/components/schemas/" + ERROR_RESPONSE_SCHEMA)));
    }

    private Content jsonContent(Schema<?> schema) {
        return new Content()
                .addMediaType(
                        APPLICATION_JSON,
                        new io.swagger.v3.oas.models.media.MediaType().schema(schema));
    }

    private Schema<?> successResponseSchema(Schema<?> dataSchema) {
        ObjectSchema schema = responseBaseSchema("Successful response", "SUCCESS", null);
        schema.addProperty("data", dataSchema == null ? new ObjectSchema() : dataSchema);
        return schema;
    }

    private Schema<?> errorResponseSchema() {
        ObjectSchema schema =
                responseBaseSchema("Error response", "INVALID_REQUEST", "Invalid request");
        schema.addProperty("data", nullableSchema());
        return schema;
    }

    private ObjectSchema responseBaseSchema(String description, String code, String message) {
        ObjectSchema schema = new ObjectSchema();
        schema.description(description);
        schema.addProperty("code", new StringSchema().example(code));
        schema.addProperty("message", new StringSchema().nullable(true).example(message));
        return schema;
    }

    private Schema<?> nullableSchema() {
        return new Schema<>().nullable(true);
    }

    private record AuthRequirement(boolean openApiKey, List<String> roles) {
        static AuthRequirement withOpenApiKey() {
            return new AuthRequirement(true, List.of());
        }

        static AuthRequirement roles(List<String> roles) {
            return new AuthRequirement(false, roles);
        }

        boolean requiresAuthentication() {
            return openApiKey || !roles.isEmpty();
        }
    }
}
