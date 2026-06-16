# HiMarket Open Source Backend Optimization Log

## Recording Rules

Each backend optimization should record the background, scope, change, compatibility, verification,
and follow-up items. Keep entries focused on one reviewable topic.

## 2026-06-14 - Stream and Optional Readability Cleanup

Background:

- The commercial backend already validated the first Stream/Optional cleanup path.
- The open source backend still had several low-risk DTO and category-service conversion paths that
  used `Collectors.toList()`, `Optional` chains over nullable SDK collections, or nested collectors
  for multi-step business assembly.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/dto/result/common/PageResult.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/result/httpapi/BackendResult.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/result/httpapi/HttpRouteResult.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/ProductCategoryServiceImpl.java`

Changes:

- Replaced read-only `Collectors.toList()` calls with `Stream.toList()`.
- Replaced nullable collection `Optional` chains in HTTP route DTO conversion with named local
  variables and explicit null checks.
- Replaced the nested category relation collector with named category ID loading, a category lookup
  map, and an explicit loop that skips stale relations.
- Updated touched Java comments and JavaDoc text to English.

Compatibility:

- No API path, DTO field, database, authentication, gateway, Nacos, or transaction behavior changed.
- Category grouping still skips stale category relations and preserves relation encounter order
  inside each product's category list.
- `Stream.toList()` returns an unmodifiable list; the changed call sites only pass or return the
  collected values and do not mutate them afterward.

Verification:

- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue Stream/Optional cleanup in small batches after review.
- Keep Nacos and Higress behavior in the open source backend; do not sync commercial-only removals.

## 2026-06-14 - Nacos and Chat Session Stream Cleanup

Background:

- The first open source Stream/Optional cleanup batch was reviewed and committed.
- `NacosServiceImpl` still had `Optional<List<...>>` and `collectingAndThen` patterns that hid
  paging assembly.
- `ChatSessionServiceImpl` still mixed result assembly with read-only `Collectors.toList()` calls
  and used `Optional` to wrap nullable attachment lists.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/NacosServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/ChatSessionServiceImpl.java`

Changes:

- Replaced Nacos nullable response collection handling with named response body variables and
  explicit empty-page branches.
- Replaced Nacos `collectingAndThen` pagination assembly with named result lists followed by
  explicit `PageResult.of(...)` calls.
- Replaced Chat session read-only result collectors with `Stream.toList()`.
- Replaced Chat session attachment `Optional` chains with local explicit null checks.
- Expanded a touched wildcard import in `ChatSessionServiceImpl`.

Compatibility:

- No Nacos feature, SDK call, REST API, DTO field, database, authentication, or transaction behavior
  changed.
- Nacos paging still uses the same page number, page size, total count, and in-memory skip/limit
  semantics.
- Chat conversation grouping and answer sequence sorting are preserved.

Verification:

- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue Stream/Optional cleanup after review, or switch to the code-language/logging batch if
  the Stream batch is considered sufficient for now.

## 2026-06-14 - DTO Result List Collection Cleanup

Background:

- After the Nacos and Chat Session batch, several DTO/result conversion classes still used
  `Collectors.toList()` for read-only conversion output.
- These classes have narrow conversion responsibilities and are low-risk candidates for another
  small Stream cleanup batch.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/dto/converter/NacosAgentConverter.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/result/portal/PortalResult.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/result/mcp/AdpMcpServerResult.java`

Changes:

- Replaced read-only conversion collectors with `Stream.toList()`.
- Removed now-unused `Collectors` imports.
- Updated touched comments and JavaDoc to English.

Compatibility:

- No API, DTO field, Nacos, ADP gateway, portal, database, authentication, or transaction behavior
  changed.
- Changed lists are conversion outputs that are not mutated after collection in these methods.

Verification:

- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue with another small Stream batch or move to the code-language/logging cleanup phase after
  review.

## 2026-06-14 - HiCoding Session Language and Logging Cleanup

Background:

- The open source backend still had Chinese JavaDoc, inline comments, validation messages, and log
  messages in the HiCoding session configuration path.
- These classes are low-risk candidates for the first code-language/logging batch because the work
  can be limited to diagnostics, DTO documentation, and local validation messages.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/session/ModelConfigResolver.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/session/McpConfigResolver.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/session/SessionConfigResolver.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/session/SessionInitializer.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/session/CliSessionConfig.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/session/CustomModelConfig.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/session/ResolvedSessionConfig.java`

Changes:

- Translated JavaDoc and inline comments to English.
- Reworded Chinese log messages into short English diagnostic messages while preserving log levels
  and structured fields.
- Translated local CustomModelConfig validation messages to English.
- Kept sensitive credential handling unchanged; API keys continue to be referenced only by presence
  or lookup status and are not logged.

Compatibility:

- No API, DTO field, database, subscription, model, MCP, Nacos, authentication, or sandbox
  initialization behavior changed.
- The resolver and initializer control flow, null handling, and returned configuration objects are
  unchanged.

Verification:

- `rg -n "[\\p{Han}]"` on the session package returned no matches.
- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue language/logging cleanup in another small reviewable batch, then combine the topic into
  one commit after review.

## 2026-06-14 - HiCoding CLI Config Generator Language and Logging Cleanup

Background:

- The HiCoding CLI configuration generator package still had Chinese JavaDoc, inline comments,
  warning logs, and local exception messages.
- This package is adjacent to the session configuration path and can be cleaned together without
  changing generated JSON fields or configuration behavior.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/cli/ConfigFileBuilder.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/cli/CliConfigGenerator.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/cli/CliConfigGeneratorRegistry.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/cli/ClaudeCodeConfigGenerator.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/cli/NacosEnvGenerator.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/cli/OpenCodeConfigGenerator.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/cli/ProtocolTypeMapper.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/cli/QoderCliConfigGenerator.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/cli/QwenCodeConfigGenerator.java`

Changes:

- Translated JavaDoc and inline comments to English.
- Reworded CLI configuration warning/error logs into English while preserving structured fields and
  log levels.
- Translated local exception messages in configuration generation helpers to English.

Compatibility:

- No generated configuration field names, file paths, provider keys, environment variable names,
  MCP merge behavior, model provider merge behavior, or Skill nacos-env content fields changed.
- JSON merge behavior and Nacos server address parsing conditions are unchanged.

Verification:

- `rg -n "[\\p{Han}]"` on the CLI package returned no matches.
- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue language/logging cleanup in sandbox/runtime/websocket packages, then combine the topic
  into one commit after review.

## 2026-06-14 - HiCoding Runtime, Filesystem, and Terminal Language Cleanup

Background:

- After session and CLI configuration cleanup, the runtime, filesystem, terminal, and remote
  workspace support classes still had Chinese JavaDoc, comments, logs, and local exception messages.
- These classes are infrastructure support code and can be cleaned without changing request routing,
  WebSocket state handling, file path resolution, or generated response shapes.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/RemoteWorkspaceService.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/filesystem/BaseUrlExtractor.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/filesystem/FileEntry.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/filesystem/FileInfo.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/filesystem/FileSystemAdapter.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/filesystem/FileSystemException.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/filesystem/PathValidator.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/filesystem/SidecarFileSystemAdapter.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/runtime/RemoteRuntimeAdapter.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/runtime/RuntimeAdapter.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/runtime/RuntimeConfig.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/runtime/RuntimeFaultNotification.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/runtime/RuntimeStatus.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/terminal/RemoteTerminalBackend.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/terminal/TerminalBackend.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/terminal/TerminalWebSocketHandler.java`

Changes:

- Translated JavaDoc and inline comments to English using multi-line JavaDoc style.
- Reworded Sidecar/file-system/terminal logs and exception messages into English.
- Preserved structured fields and existing exception/error-code types.

Compatibility:

- No HTTP endpoint path, request body, response shape, workspace path resolution, file tree mapping,
  terminal reconnect behavior, WebSocket control-message handling, or runtime state transition
  changed.
- Only human-readable diagnostics and comments changed.

Verification:

- `rg -n "[\\p{Han}]"` on the touched runtime, terminal, filesystem, and RemoteWorkspaceService
  files returned no matches.
- `rg -n "^\\s*/\\*\\* .+ \\*/"` on the touched HiCoding packages returned no matches.
- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue language/logging cleanup in the remaining sandbox and websocket packages, then combine
  the topic into one commit after review.

## 2026-06-14 - HiCoding Sandbox and WebSocket Language Cleanup

Background:

- The remaining HiCoding sandbox and WebSocket packages still had Chinese JavaDoc, inline comments,
  frontend status messages, troubleshooting suggestions, logs, and local exception messages.
- These packages expose user-visible initialization and reattach status messages, so the cleanup
  needed to preserve protocol fields while translating human-readable text.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/sandbox/**`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/sandbox/init/**`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/websocket/WebSocketPingScheduler.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/websocket/HiCodingMessageRouter.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/websocket/HiCodingConnectionManager.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/websocket/HiCodingWebSocketHandler.java`

Changes:

- Translated sandbox provider, HTTP client, initialization phase, and WebSocket JavaDoc/comments to
  English using multi-line JavaDoc style.
- Reworded sandbox initialization, Sidecar, configuration injection, Skill download, reattach, and
  WebSocket diagnostic logs into English.
- Translated frontend-facing sandbox status/progress/error messages and troubleshooting
  suggestions, while keeping JSON-RPC methods, phase names, status keys, provider keys, and field
  names unchanged.
- Translated local validation and exception messages in sandbox providers and path guards.

Compatibility:

- No JSON-RPC method name, status key, phase name, sandbox type value, HTTP API path, payload field,
  workspace path resolution, Sidecar session attach behavior, reattach flow, or message queue
  behavior changed.
- Only comments and human-readable diagnostics/status text changed.

Verification:

- `rg -n "[\\p{Han}]"` on the sandbox and websocket packages returned no matches.
- `rg -n "^\\s*/\\*\\* .+ \\*/"` on the sandbox and websocket packages returned no matches.
- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Review the complete language/logging cleanup batch, then commit it as one combined topic commit.

## 2026-06-15 - CLI and MCP Read-only List Collection Cleanup

Background:

- A leftover HiCoding model resolver change had already moved one read-only subscription list
  collection from `Collectors.toList()` to `Stream.toList()`.
- Nearby CLI provider and MCP resolver/orchestrator code still used `Collectors.toList()` for
  read-only results that are only iterated, checked for emptiness, returned, or passed to repository
  and service query methods.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/session/ModelConfigResolver.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/session/McpConfigResolver.java`
- `himarket-server/src/main/java/com/alibaba/himarket/controller/CliProviderController.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/mcp/McpTransportResolver.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/mcp/McpSandboxOrchestrator.java`

Changes:

- Replaced read-only `Collectors.toList()` calls with `Stream.toList()`.
- Removed now-unused `Collectors` imports where all collector usages were eliminated.
- Kept `Collectors.toMap()` and grouping collectors where map/grouping semantics are still needed.

Compatibility:

- No REST path, JSON field, subscription filtering, product lookup, MCP endpoint lookup, sandbox
  deployment, or auth-header behavior changed.
- Changed lists are not mutated after collection in these methods.

Verification:

- `rg -n "Collectors\\.toList|java\\.util\\.stream\\.Collectors"` on the touched files showed no
  remaining `Collectors.toList()` usages; remaining `Collectors` imports are still used for map
  collection.
- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue Stream/Optional cleanup in another small batch, avoiding large gateway/operator classes
  until they can be reviewed separately.

## 2026-06-15 - Utility and Sandbox Read-only List Collection Cleanup

Background:

- After the CLI and MCP resolver batch, a few small controller, utility, sandbox, and vendor paths
  still used `Collectors.toList()` for read-only list results.
- These call sites are narrow and do not mutate the collected lists after construction.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/controller/McpServerController.java`
- `himarket-server/src/main/java/com/alibaba/himarket/core/utils/K8sClientUtils.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/SandboxServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/vendor/McpVendorServiceImpl.java`

Changes:

- Replaced read-only `Collectors.toList()` calls with `Stream.toList()`.
- Removed now-unused `Collectors` imports where all collector usages were eliminated.
- Kept the `Collectors` import in `McpVendorServiceImpl` because it is still used for `toSet()`.

Compatibility:

- No controller path, response field, Kubernetes namespace lookup, sandbox list filtering, vendor
  lookup, or page assembly behavior changed.
- Changed lists are only returned, wrapped in a page result, or passed along as read-only results.

Verification:

- `rg -n "Collectors\\.toList|java\\.util\\.stream\\.Collectors"` on the touched files showed no
  remaining `Collectors.toList()` usages; the remaining `Collectors` import is still used for
  `toSet()`.
- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue Stream/Optional cleanup in small batches. Large gateway/operator classes should remain
  separate review units.

## 2026-06-15 - Support and Consumer Read-only List Collection Cleanup

Background:

- After the utility/sandbox batch, a few support converter, consumer subscription, and AIRegistry
  skill paths still used `Collectors.toList()` for read-only conversion output.
- These call sites are local result assembly paths and do not rely on mutable list semantics.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/support/mcp/OpenAPIToolsConfigConverter.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/ConsumerServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/AiRegistrySkillServiceImpl.java`

Changes:

- Replaced read-only `Collectors.toList()` calls with `Stream.toList()`.
- Removed now-unused `Collectors` imports.

Compatibility:

- No MCP tools config fields, consumer subscription response fields, product lookup behavior,
  AIRegistry Skill version sorting, page assembly, or download-count behavior changed.
- Changed lists are only returned, assigned to DTO/config objects, or passed to existing service
  query methods.

Verification:

- `rg -n "Collectors\\.toList|java\\.util\\.stream\\.Collectors"` on the touched files returned no
  matches.
- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue Stream/Optional cleanup in small batches. `ProductServiceImpl`, `McpServerServiceImpl`,
  and gateway/operator classes should remain separate review units.

## 2026-06-15 - Product Service Read-only List Collection Cleanup

Background:

- `ProductServiceImpl` still had several `Collectors.toList()` usages in product listing,
  subscription listing, product enrichment, and filter pagination paths.
- The affected lists are used as read-only DTO results, lookup IDs, filtered product sets, or
  repository query arguments.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/ProductServiceImpl.java`

Changes:

- Replaced read-only `Collectors.toList()` calls with `Stream.toList()`.
- Kept `Collectors.toMap()` usages for lookup map assembly.
- Left existing Optional logic unchanged in this batch.

Compatibility:

- No product filtering, sorting, pagination, category enrichment, product-ref enrichment,
  subscription listing, repository lookup, or response field behavior changed.
- `fillProducts(...)` still receives lists for iteration and object enrichment; it does not mutate
  list structure.

Verification:

- `rg -n "Collectors\\.toList|Collectors\\.toMap|Collectors\\.groupingBy|java\\.util\\.stream\\.Collectors|Optional\\.ofNullable"`
  on `ProductServiceImpl` showed no remaining `Collectors.toList()` usages; `Collectors.toMap()`
  usages remain where required.
- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue Stream/Optional cleanup with `McpServerServiceImpl` or other single-owner service
  batches before tackling gateway/operator classes.

## 2026-06-15 - MCP Server Service Read-only List Collection Cleanup

Background:

- `McpServerServiceImpl` still had several `Collectors.toList()` usages in metadata listing,
  endpoint listing, published MCP listing, and current-user endpoint assembly.
- The affected lists are read-only DTO results, lookup IDs, or repository query arguments.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/McpServerServiceImpl.java`

Changes:

- Replaced read-only `Collectors.toList()` calls with `Stream.toList()`.
- Kept `Collectors.toMap()` usages for lookup map assembly.

Compatibility:

- No MCP metadata filtering, endpoint lookup, public metadata conversion, published MCP pagination,
  current-user endpoint assembly, product enrichment, or resolved config behavior changed.
- Changed lists are only returned, wrapped in page results, or passed to repository query methods.

Verification:

- `rg -n "Collectors\\.toList|Collectors\\.toMap|Collectors\\.groupingBy|java\\.util\\.stream\\.Collectors"`
  on `McpServerServiceImpl` showed no remaining `Collectors.toList()` usages; `Collectors.toMap()`
  usages remain where required.
- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue Stream/Optional cleanup with another single-owner service batch, then treat gateway and
  operator classes as larger dedicated review units.

## 2026-06-15 - Product and Chat Stream/Optional Flow Cleanup

Background:

- The Stream/Optional cleanup should not stop at replacing `Collectors.toList()`.
- `ProductServiceImpl` still used Optional for local default-object construction, config parsing,
  and product-name filtering.
- `ChatService` still used a stream pipeline for MCP transport config assembly while also mutating
  the produced config object with credential headers and query params.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/ProductServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hichat/service/ChatService.java`

Changes:

- Replaced local Optional default-object construction with explicit local variables and null
  branches.
- Replaced Optional-based MCP config parsing with explicit null and parsed-result checks.
- Replaced product config filter stream logic with a named loop and kept common product filters in
  `buildSpecification(param)`, matching the commercial backend flow.
- Replaced MCP transport config stream assembly with a loop because the flow performs filtering,
  config conversion, and credential field assignment.
- Kept simple `toSet()` and `groupingBy()` collectors in `ChatService` where they express plain
  collection aggregation.

Compatibility:

- No product creation, product source update, MCP tool sync, product filter pagination, chat
  history grouping, attachment lookup, MCP credential assignment, or response shape changed.
- The explicit loops preserve the same product-ref filtering and object-enrichment behavior.
- Cross-checked the related `ProductServiceImpl` and `ChatService` flows against the commercial
  `apigw-himarket` repository; aligned `listProductsWithFilter` so common filters are handled by
  `buildSpecification(param)`.

Verification:

- `rg -n "Optional\\.ofNullable|Collectors\\.toList\\(|java\\.util\\.stream\\.Collectors"` on the
  touched ProductService/ChatService files showed no ProductService Optional chains and no
  `Collectors.toList()` usages; remaining ChatService collectors are `toSet()`/`groupingBy()`.
- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue applying the fuller Stream/Optional standard to a few files per batch, prioritizing
  complex stream pipelines and Optional chains before sweeping mechanical `.toList()` changes.

## 2026-06-15 - Service Flow Stream/Optional Cleanup

Background:

- Several service classes still used Optional chains for update-name conflict checks and complex
  `groupingBy(..., Collectors.toList())` pipelines for chat conversation assembly.
- Portal listing also used `Collectors.toList()` and `forEach` for object mutation.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/ChatSessionServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/PortalServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/ProductCategoryServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/AiRegistryServiceImpl.java`

Changes:

- Replaced chat conversation grouping collectors with explicit `LinkedHashMap` and `TreeMap`
  grouping loops, preserving encounter order and sequence sorting.
- Replaced Optional-based update-name conflict checks with named `requestedName` variables and
  explicit repository boundary checks.
- Replaced Portal domain ID collection with `.toList()` and used an explicit loop for assigning
  loaded domain entities.
- Replaced the Portal enabled-OIDC Optional chain with a direct loop that exits once an enabled
  provider is found.

Compatibility:

- Chat conversation grouping still preserves the original grouping order and sorted sequence order.
- Explicit chat grouping keeps the previous `Collectors.groupingBy` null-key boundary via
  `Objects.requireNonNull(...)`.
- Product category, portal, and AIRegistry conflict checks still query the same repositories before
  applying updates; conflict messages now use the requested name.
- Cross-checked the related commercial implementations. `ProductCategoryServiceImpl#update...`,
  `PortalServiceImpl#updatePortal`, and `ChatSessionServiceImpl` guided the flow. The commercial
  `PortalServiceImpl#listPortals` appears to fill a temporary result list and then return a newly
  converted page result, so that part was not copied.

Verification:

- `rg -n "Optional\\.ofNullable|Collectors\\.toList\\(|java\\.util\\.Optional|/\\*\\* [^*].* \\*/"`
  on the touched service files showed no remaining Optional business chains, no
  `Collectors.toList()` usages, and no one-line Javadoc.
- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue with gateway/operator Stream/Optional cleanup as dedicated batches because those methods
  mix external SDK calls and transport-specific behavior.

## 2026-06-15 - Stream/Optional Cleanup Closure

Background:

- After the service-level batches, remaining Stream/Optional cleanup points were concentrated in
  gateway operators, authentication/IdP flows, MCP config conversion, token parsing, download-count
  synchronization, and a dead commented Higress DTO file.
- The goal of this batch was to close all remaining `Optional.ofNullable(...)` business chains and
  `Collectors.toList()` usages while preserving repository `Optional<T>` return boundaries.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/service/gateway/*.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/OidcServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/OAuth2ServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/IdpServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/ConsumerServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/DeveloperServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/SkillServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/WorkerServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/task/DownloadCountSyncTask.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hichat/service/AbstractLlmService.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/sandbox/init/SkillDownloadPhase.java`
- `himarket-server/src/main/java/com/alibaba/himarket/core/utils/TokenUtil.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/result/mcp/McpConfigResult.java`
- `himarket-server/src/main/java/com/alibaba/himarket/support/mcp/OpenAPIToolsConfigConverter.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/result/higress/HigressRouteResult.java`

Changes:

- Replaced remaining `Collectors.toList()` calls with `.toList()` where the result is read-only.
- Replaced Optional chains around external responses, gateway credentials, portal settings,
  OIDC/OAuth2 config lookup, MCP tool conversion, token cookie lookup, and model route selection
  with named variables and explicit `if`/`for` flow.
- Replaced grouping pipelines that immediately triggered synchronization/deletion side effects with
  explicit grouping maps and loops.
- Deleted the commented-out `HigressRouteResult` file because it contained no active class and only
  obsolete commented code.

Compatibility:

- Repository interfaces still return `Optional<T>`; those are intentional boundary types and were
  left unchanged.
- Simple Stream usage remains for plain collection transforms such as `map`, `filter`, `toList`,
  `toMap`, `toSet`, and `groupingBy`.
- Cross-checked the commercial `AIGWOperator` and `APIGOperator`; they still contain a few Optional
  response parsing chains, so this batch preserved their behavior but used explicit open-source
  flow instead of copying those chains.
- Gateway and credential flows keep the same fallback behavior for missing response bodies,
  consumer credentials, default domains, and protocol defaults.

Verification:

- `rg -n "Optional\\.ofNullable|Collectors\\.toList\\(" himarket-server/src/main/java himarket-dal/src/main/java`
  returned no matches.
- `rg -n "\\.stream\\(\\).*forEach|\\.collect\\(\\s*Collectors\\.groupingBy(?s:.{0,260})\\.forEach"`
  returned no matches.
- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue with the next non-Stream/Optional backend theme, likely import/language/comment cleanup
  or gateway-specific readability issues.

## 2026-06-15 - Gateway Comment and Diagnostic Language Cleanup

Background:

- Phase 2 requires Java comments, logs, and local diagnostics to use English in main backend code.
- The gateway ADP/Apsara path still had Chinese one-line JavaDoc, process-history comments, and
  local gateway diagnostic messages.
- The commercial backend has APIG/AIGW gateway operators but no equivalent ADP/Apsara classes, so
  this batch used the commercial code only as a style reference and did not copy behavior.

Scope:

- `himarket-dal/src/main/java/com/alibaba/himarket/support/gateway/AdpAIGatewayConfig.java`
- `himarket-dal/src/main/java/com/alibaba/himarket/support/gateway/ApsaraGatewayConfig.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/gateway/client/AdpAIGatewayClient.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/gateway/AdpAIGatewayOperator.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/gateway/ApsaraGatewayOperator.java`

Changes:

- Converted touched one-line JavaDoc comments to the project-preferred multi-line style.
- Translated Chinese JavaDoc, inline comments, logs, and local exception messages to English.
- Removed redundant comments that only repeated field names, variable names, or historical fix notes.
- Kept the Chinese `message.contains("不存在")` checks because they intentionally match localized
  gateway responses for idempotent authorization revocation.

Compatibility:

- No gateway SDK call, HTTP endpoint, DTO field, credential mapping, consumer authorization, or
  fallback behavior changed.
- ADP Basic authentication, Apsara credential mapping, default protocol values, and authorization
  not-found tolerance are preserved.

Verification:

- `rg -n "[\\p{Han}]|/\\*\\* [^*].* \\*/"` on the touched files shows only the intentional
  localized external-response checks.
- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue Phase 2 language cleanup in SLS, MCP DTO/service, and Nacos-facing code as separate
  reviewable batches.

## 2026-06-15 - SLS DTO and Service Contract Language Cleanup

Background:

- The SLS and DBCollector API contract layer still had Chinese JavaDoc and field comments.
- This area is open-source specific; the commercial backend has no matching SLS/DBCollector backend
  files to copy, so this batch follows the open-source code's existing contracts.

Scope:

- `himarket-dal/src/main/java/com/alibaba/himarket/support/enums/SlsAuthType.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/SlsLogService.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/DBCollectorService.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/gateway/factory/SlsClientFactory.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/sls/*.java`

Changes:

- Converted SLS DTO, service interface, enum, and factory JavaDoc comments to English.
- Expanded touched one-line JavaDoc into the project-preferred multi-line style.
- Reworded the unsupported STS factory diagnostic into natural English.
- Kept JSON field names, validation annotations, SLS query parameters, and response structures
  unchanged.

Compatibility:

- No API path, request/response field, authentication selection, SLS client creation, or query
  behavior changed.
- The MCP route_name compatibility notes remain documented in English.

Verification:

- `rg -n "[\\p{Han}]|/\\*\\* [^*].* \\*/"` on the touched SLS DTO/service/factory files returned no
  matches.
- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue Phase 2 language cleanup in `SlsLogServiceImpl`, `DBCollectorServiceImpl`, and the
  preset SQL registries as a separate batch because those files are much larger.

## 2026-06-15 - SLS Implementation and Preset Registry Language Cleanup

Background:

- After cleaning the SLS DTO and service contract layer, the implementation and preset registry
  files still had Chinese JavaDoc and inline comments.
- These files are open-source specific; no matching commercial implementation exists, so the batch
  keeps open-source SQL templates and behavior intact.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/dto/converter/SlsResponseConverter.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/SlsLogServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/DBCollectorServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/gateway/factory/SlsPresetSqlRegistry.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/gateway/factory/DBCollectorPresetSqlRegistry.java`

Changes:

- Translated implementation, converter, and preset registry JavaDoc and inline comments to English.
- Removed comments that only repeated the next statement, while keeping filter mapping and index
  strategy notes that are useful for maintenance.
- Corrected one DBCollector comment to match the existing `/mcp-servers` path-prefix check.
- Left SLS SQL templates, DBCollector SQL templates, field aliases, scenario names, and response
  conversion logic unchanged.

Compatibility:

- No query SQL, filter mapping, index configuration behavior, controller contract, or response
  structure changed.
- Existing SLS and DBCollector scenario names remain frontend/backend contracts.

Verification:

- `rg -n "[\\p{Han}]|/\\*\\* [^*].* \\*/"` on the touched implementation/registry/converter files
  returned no matches.
- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue Phase 2 cleanup in Nacos and MCP-related service/DTO files.

## 2026-06-15 - Nacos and Vendor Adapter Language Cleanup

Background:

- Phase 2 still had Chinese JavaDoc, inline comments, and local diagnostics in the Nacos management
  path and external MCP vendor adapters.
- The commercial backend has no matching Nacos or external vendor adapter implementation, so this
  batch used the commercial code only as a style reference and kept open-source behavior intact.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/controller/NacosController.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/NacosService.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/NacosServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/vendor/McpVendorService.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/vendor/McpVendorServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/vendor/VendorAdapterRegistry.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/vendor/McpRegistryAdapter.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/vendor/ModelScopeAdapter.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/vendor/LobeHubAdapter.java`

Changes:

- Translated Nacos service/controller JavaDoc, inline comments, and local exception messages to
  English.
- Removed redundant numbered step comments and section marker comments that repeated method flow.
- Preserved comments that explain Nacos SDK paging, standard AgentCard serialization, A2A route
  behavior, MCP Registry cursor paging, and LobeHub JWT client assertion authentication.
- Translated vendor adapter JavaDoc and local vendor/OAuth diagnostic messages to English.
- Expanded touched one-line JavaDoc into the project-preferred multi-line style.

Compatibility:

- No REST path, request/response field, Nacos SDK call, MCP vendor API call, pagination behavior,
  OAuth token flow, cache policy, or import mapping changed.
- Nacos default-instance handling, namespace verification, Agent/MCP/Skill listing, and vendor
  connection construction remain behaviorally unchanged.

Verification:

- `rg -n "[\\p{Han}]|/\\*\\* [^*].* \\*/"` on the touched Java files returned no matches.
- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue Phase 2 cleanup in MCP DTO/service and HiCoding sandbox/runtime-adjacent files.

## 2026-06-15 - MCP Sandbox and Meta DTO Language Cleanup

Background:

- The MCP sandbox deployment and metadata DTO path still had Chinese JavaDoc, section marker
  comments, validation messages, and deployment logs.
- These files are open-source specific and model the sandbox/endpoint workflow, so this batch keeps
  the existing deployment and response semantics while normalizing language.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/mcp/SaveMcpMetaParam.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/mcp/UpdateServiceIntroParam.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/result/mcp/McpMetaPublicResult.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/result/mcp/McpMetaResult.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/mcp/McpConnectionConfig.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/mcp/McpSandboxDeployListener.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/mcp/McpSandboxDeployStrategy.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/mcp/McpSandboxOrchestrator.java`

Changes:

- Translated MCP metadata DTO JavaDoc, field comments, validation messages, and computed-field
  notes to English.
- Translated sandbox deployment strategy JavaDoc and retained parameter details for CRD, Secret,
  endpoint, namespace, and resource-spec boundaries.
- Translated sandbox deployment listener/orchestrator logs and comments to English.
- Removed redundant step comments and section marker comments while preserving explanations for
  transaction-after-commit deployment, deploy result parsing, SSE endpoint normalization, and
  rollback behavior.
- Expanded touched one-line JavaDoc into the project-preferred multi-line style.

Compatibility:

- No DTO field name, validation group, REST contract, JSON field, connection-config parsing,
  endpoint state transition, event publishing, K8s CRD deployment, Secret cleanup, or rollback
  behavior changed.
- Validation annotations are unchanged; only the displayed validation messages were translated.

Verification:

- `rg -n "[\\p{Han}]|/\\*\\* [^*].* \\*/|// ={3,}"` on the touched MCP files returned no matches.
- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue Phase 2 cleanup in the remaining MCP service/DTO files or move to gateway/IdP/security
  contract comments as the next language-cleanup batch.

## 2026-06-15 - MCP API DTO and Helper Language Cleanup

Background:

- After the sandbox/meta batch, several smaller MCP API DTOs, repository contracts, event payloads,
  and helper classes still had Chinese JavaDoc, one-line JavaDoc, validation messages, TODOs, and
  diagnostic logs.
- These files define contracts around Open API registration, subscription, endpoint visibility,
  protocol normalization, and tools_config parsing, so the cleanup focuses on wording only.

Scope:

- `himarket-dal/src/main/java/com/alibaba/himarket/repository/McpServerEndpointRepository.java`
- `himarket-dal/src/main/java/com/alibaba/himarket/repository/McpServerMetaRepository.java`
- `himarket-dal/src/main/java/com/alibaba/himarket/support/consumer/McpAuthConfig.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/mcp/SubscribeMcpParam.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/mcp/McpConnectParam.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/mcp/SaveMcpEndpointParam.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/mcp/RegisterMcpParam.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/mcp/DeploySandboxParam.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/result/mcp/McpEndpointResult.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/result/mcp/McpConnectResult.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/result/mcp/MyEndpointResult.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/result/mcp/McpMetaSimpleResult.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/result/mcp/McpMetaDetailResult.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/mcp/McpSandboxDeployEvent.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/mcp/McpSandboxUndeployEvent.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/mcp/McpProtocolUtils.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/mcp/McpToolsConfigParser.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/mcp/SelfHostedDeployStrategy.java`

Changes:

- Translated MCP API DTO JavaDoc, field comments, validation messages, and result visibility notes
  to English.
- Converted touched one-line JavaDoc to the multi-line style.
- Translated repository comments that document endpoint visibility, public endpoint lookup, and
  N+1 avoidance.
- Translated sandbox deployment event documentation while preserving the transaction-after-commit
  and POJO event explanation.
- Translated protocol normalization and tools_config JSON/YAML parsing comments and logs.
- Translated SELF_HOSTED placeholder TODOs and unsupported-operation messages.

Compatibility:

- No repository method name, validation annotation, DTO field, JSON contract, protocol
  normalization rule, tools_config parsing behavior, event payload, or unsupported SELF_HOSTED
  behavior changed.
- Validation annotations are unchanged; only the displayed validation messages were translated.

Verification:

- `rg -n "[\\p{Han}]|/\\*\\* [^*].* \\*/|// ={3,}"` on the touched files returned no matches.
- `git diff --check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q spotless:check` passed.
- `/Users/zhaoh/app/maven/apache-maven-3.8.1/bin/mvn -q -DskipTests compile` passed.

Follow-up:

- Continue Phase 2 cleanup in the larger MCP runtime/service implementations, such as
  `McpTransportResolver`, `McpConfigSyncHelper`, and `AgentRuntimeDeployStrategy`, as separate
  reviewable batches.

## 2026-06-15 - MCP Runtime Implementation Language Cleanup

Background:

- The larger MCP runtime/service implementation files still had Chinese JavaDoc, inline comments,
  deployment logs, and diagnostic exception messages.
- These files cover open-source specific runtime deployment and endpoint resolution paths. The
  commercial backend does not provide a directly matching AGENT_RUNTIME CRD implementation, so this
  batch keeps the existing open-source behavior and normalizes language only.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/service/mcp/McpTransportResolver.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/mcp/McpConfigSyncHelper.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/mcp/AgentRuntimeDeployStrategy.java`

Changes:

- Translated MCP transport resolution logs and sandbox authentication diagnostics to English.
- Translated MCP config sync helper comments, endpoint sync logs, icon/config parsing logs, and
  endpoint extraction errors to English.
- Translated AGENT_RUNTIME deployment JavaDoc, CRD template comments, Secret/CRD lifecycle logs,
  endpoint polling diagnostics, env/header/query routing notes, and resourceSpec errors to English.
- Removed section marker comments and expanded touched one-line JavaDoc into the preferred
  multi-line style.

Compatibility:

- No endpoint selection, subscription validation, credential lookup, auth header resolution,
  ProductRef sync, public endpoint sync, connectionConfig conversion, K8s Secret creation/deletion,
  CRD template selection/rendering, resource placeholder default, endpoint polling, or fallback URL
  extraction behavior changed.
- Validation and exception types are unchanged; only diagnostic text was translated.

Verification:

- `rg -n "[\\p{Han}]|/\\*\\* [^*].* \\*/|// ={3,}"` on the touched MCP runtime files returned no
  matches.
- `git diff --check` passed.
- `./mvnw -q spotless:check -DskipTests` passed.
- `./mvnw -q -DskipTests compile` passed.

Follow-up:

- Continue Phase 2 cleanup in the remaining MCP service, gateway, IdP, and security implementation
  files.

## 2026-06-15 - MCP Service Contract and Orchestration Language Cleanup

Background:

- The MCP service interface, service orchestration implementations, and core MCP entity/enum files
  still had Chinese JavaDoc, one-line JavaDoc, section marker comments, logs, and diagnostic
  exceptions.
- No matching files were found in the commercial backend for these open-source MCP service classes,
  so this batch follows the existing open-source semantics and performs wording cleanup only.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/service/McpServerService.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/McpServerServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/McpSandboxDeployServiceImpl.java`
- `himarket-dal/src/main/java/com/alibaba/himarket/entity/McpServerMeta.java`
- `himarket-dal/src/main/java/com/alibaba/himarket/entity/McpServerEndpoint.java`
- `himarket-dal/src/main/java/com/alibaba/himarket/support/enums/McpHostingType.java`
- `himarket-dal/src/main/java/com/alibaba/himarket/support/enums/McpEndpointStatus.java`
- `himarket-dal/src/main/java/com/alibaba/himarket/support/enums/McpOrigin.java`

Changes:

- Translated MCP service contract JavaDoc and expanded one-line JavaDoc into the preferred
  multi-line style.
- Translated MCP registration, sandbox deployment, deletion, tool refresh, and endpoint diagnostic
  messages to English.
- Translated sandbox deployment dispatch comments, unsupported-type errors, and sandbox status
  errors.
- Translated MCP entity and enum documentation while preserving field, enum, and JPA metadata.
- Removed section marker comments in the service implementation.

Compatibility:

- No method signature, repository call, transaction boundary, Product/MCP metadata relationship,
  sandbox deploy/undeploy orchestration, endpoint deletion, tool refresh flow, entity column
  mapping, enum value, or JSON field sanitation behavior changed.
- Only diagnostic text, comments, and JavaDoc style were changed.

Verification:

- `rg -n "[\\p{Han}]|/\\*\\* [^*].* \\*/|// ={3,}"` on the touched MCP service/entity/enum files
  returned no matches.
- `git diff --check` passed.
- `./mvnw -q spotless:check -DskipTests` passed.
- `./mvnw -q -DskipTests compile` passed.

Follow-up:

- Continue Phase 2 cleanup in gateway operator/client, sandbox health checks, skill parsing, and
  remaining DTO/controller validation text.

## 2026-06-15 - Sandbox and K8s Language Cleanup

Background:

- The sandbox service contract, sandbox implementation, scheduled health check, K8s client helper,
  and sandbox DTO/result files still had Chinese JavaDoc, inline comments, validation messages,
  operational logs, and diagnostic exception messages.
- No matching files were found in the commercial backend for this sandbox/K8s group, so this batch
  keeps the open-source implementation semantics and only normalizes wording.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/service/McpSandboxDeployService.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/SandboxService.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/SandboxServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/sandbox/SandboxHealthCheckTask.java`
- `himarket-server/src/main/java/com/alibaba/himarket/core/utils/K8sClientUtils.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/sandbox/ClusterInfoParam.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/sandbox/ImportSandboxParam.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/result/sandbox/SandboxSimpleResult.java`

Changes:

- Translated sandbox service and MCP sandbox deploy service JavaDoc to English.
- Translated sandbox import/update/delete, K8s cluster connection, namespace listing, current-user,
  and active-deployment diagnostics to English.
- Translated sandbox health check comments, status messages, and operational logs to English.
- Translated K8s client cache comments and logs while keeping the connectivity verification and
  rebuild behavior documented.
- Aligned touched logs with the logging guideline style: short English event messages,
  lowerCamelCase key fields, throwable stack retention for error logs, and no MDC requirement for
  the open-source backend.
- Translated sandbox validation messages and expanded the touched one-line JavaDoc.

Compatibility:

- No service method signature, repository query, transaction boundary, K8s client cache policy,
  health-check schedule, RUNNING/ERROR status value, namespace listing, cluster metadata extraction,
  or sandbox deletion guard changed.
- Validation annotations are unchanged; only displayed validation messages were translated.

Verification:

- `rg -n "[\\p{Han}]|/\\*\\* [^*].* \\*/|// ={3,}"` on the touched sandbox/K8s files returned no
  matches.
- `git diff --check` passed.
- `./mvnw -q spotless:check -DskipTests` passed.
- `./mvnw -q -DskipTests compile` passed.

Follow-up:

- Continue Phase 2 cleanup in gateway operator/client files, skill parsing/download paths, and
  remaining DTO/controller validation text.

## 2026-06-15 - Logging Guideline Alignment

Background:

- After the initial language cleanup, several foundational logs still did not fully match the
  logging guideline style: exception logs used artificial prefixes, gateway client logs lacked
  stable key fields, and JSON utility diagnostics needed a readability pass.
- The commercial backend has corresponding `ExceptionAdvice`, `JsonUtil`, `APIGClient`, and
  `PopGatewayClient` files. Their logging direction was used as a reference, but the open-source
  backend intentionally does not add requestId/action MDC fields.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/core/advice/ExceptionAdvice.java`
- `himarket-dal/src/main/java/com/alibaba/himarket/utils/JsonUtil.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/gateway/client/APIGClient.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/gateway/client/PopGatewayClient.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/gateway/client/HigressClient.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/gateway/factory/SlsClientFactory.java`
- `himarket-server/src/main/java/com/alibaba/himarket/config/ObservabilityConfig.java`

Changes:

- Removed artificial exception-log prefixes and converted global exception logs to short English
  messages with key fields.
- Kept `JsonUtil` parse diagnostics direct and simple, including the original JSON value where the
  helper is already used for local troubleshooting.
- Aligned APIG, POP, Higress, and SLS client logs with `dependency`, `operation`, `status`,
  `errorType`, and `errorMessage` fields.
- Stopped logging Higress response bodies and HTTP error bodies directly.
- Avoided logging the full DB datasource URL in observability startup logs.

Compatibility:

- No exception mapping, response payload contract, JSON parse/serialize behavior, gateway request
  execution, retry behavior, SLS client creation, or observability source selection changed.
- Logging remains MDC-free in the open-source backend.

Verification:

- `rg -n "[\\p{Han}]|/\\*\\* [^*].* \\*/|// ={3,}|\\[Business Exception\\]|\\[Validation Exception\\]|\\[System Exception\\]"`
  on the touched files returned no matches.
- `git diff --check` passed.
- `./mvnw -q spotless:check -DskipTests` passed.

Follow-up:

- Continue aligning remaining gateway operator/client and skill download logs with the same logging
  guideline.

## 2026-06-15 - Gateway Client Logging Expansion

Background:

- Gateway clients and basic operators still had legacy `Error ...` logs, Chinese comments, section
  marker comments, and logs that did not consistently expose stable key fields.
- Commercial `GatewayClient` and `APIGOperator` were checked for comparable behavior. Their
  endpoint-fallback semantics matched the open-source implementation, while open-source logging
  remains MDC-free.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/service/gateway/client/ApsaraGatewayClient.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/gateway/client/AdpAIGatewayClient.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/gateway/client/GatewayClient.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/gateway/APIGOperator.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/gateway/HigressOperator.java`

Changes:

- Replaced repeated Apsara client error logs with a shared helper that records `dependency`,
  `operation`, `errorType`, and `errorMessage`.
- Converted Apsara client section markers and Chinese JavaDoc/comments to English.
- Aligned ADP, APIG, and Higress logs with external-dependency logging fields.
- Avoided logging gateway request bodies; only body field counts or presence flags are logged.
- Translated Higress consumer-existence TODO and gateway endpoint fallback comments.

Compatibility:

- No gateway request action, path, method, payload, retry behavior, endpoint fallback, consumer
  lookup, authorization revocation, or URI construction behavior changed.
- Logs remain MDC-free in the open-source backend.

Verification:

- `rg -n "[\\p{Han}]|// ={3,}|/\\*\\* [^*].* \\*/|log\\.(debug|info|warn|error)\\(\"Error|requestBody|body=|\\["`
  on the touched gateway files returned no actionable matches.
- `git diff --check` passed.

Follow-up:

- Continue applying the logging guideline to the larger Apsara/ADP gateway operators and remaining
  IdP/Nacos/skill download logs.

## 2026-06-15 - Gateway Operator Logging Expansion

Background:

- The large ADP and Apsara gateway operators still contained legacy `Error ...` logs and ADP-side
  info logs that printed full request/response payloads.
- The commercial backend does not contain matching ADP/Apsara operator implementations, so this
  batch followed the shared gateway logging guideline directly and kept the open-source backend
  MDC-free.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/service/gateway/AdpAIGatewayOperator.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/gateway/ApsaraGatewayOperator.java`

Changes:

- Converted gateway operator logs to short English event messages with stable key fields such as
  `dependency`, `operation`, `gatewayId`, `consumerId`, `mcpServerName`, and `modelApiId`.
- Removed ADP logs that printed complete request bodies, response bodies, or full gateway URLs.
- Kept throwable stacks on error logs and added `errorType`/`errorMessage` metadata where useful.
- Replaced localized not-found string literals with named constants using escaped text, preserving
  idempotent revocation behavior without visible non-English source text.
- Normalized related exception messages from `Error ...` to `Failed ...`.

Compatibility:

- No gateway API endpoint, request body construction, response parsing, authorization flow,
  idempotent revocation handling, or consumer ID fallback behavior changed.
- Apsara model config fetching now closes its SDK client in a `finally` block, matching the
  surrounding operator methods.

Verification:

- Targeted `rg` scan on both gateway operator files returned no actionable Chinese text, one-line
  JavaDoc, `requestBody=`/`responseBody=` log output, or legacy `Error ...` log template matches.
- `git diff --check` passed for the touched operator files.
- `./mvnw -q spotless:check -DskipTests` passed.
- `./mvnw -q -DskipTests compile` passed.

## 2026-06-15 - Skill Package Text and Download Logging Cleanup

Background:

- The SKILL.md parser/builder helpers and Skill/Worker ZIP download paths still had Chinese
  JavaDoc, inline comments, and validation messages.
- The Nacos ZIP download logs printed the full download URL after password masking. The URL is now
  replaced with structured context fields to avoid exposing full request URLs in logs.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/core/skill/SkillMdBuilder.java`
- `himarket-server/src/main/java/com/alibaba/himarket/core/skill/FileTreeBuilder.java`
- `himarket-server/src/main/java/com/alibaba/himarket/core/skill/SkillMdDocument.java`
- `himarket-server/src/main/java/com/alibaba/himarket/core/skill/SkillMdParser.java`
- `himarket-server/src/main/java/com/alibaba/himarket/core/skill/SkillZipParser.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/SkillServiceImpl.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/impl/WorkerServiceImpl.java`

Changes:

- Translated SKILL.md parser/builder JavaDoc, inline comments, and local validation messages to
  English.
- Expanded touched one-line JavaDoc comments to the preferred multi-line style.
- Aligned Skill/Worker Nacos ZIP download logs with `dependency`, `operation`, resource identity,
  status, and error metadata fields.
- Avoided logging the full Nacos download URL while preserving the existing authenticated download
  request construction.

Compatibility:

- No SKILL.md parsing rule, ZIP entry traversal, binary resource detection, Nacos download URL
  construction, fallback behavior, response header, or ZIP streaming behavior changed.

Verification:

- Targeted `rg` scan on the touched files returned no actionable Chinese text, one-line JavaDoc,
  full URL log output, or legacy success/error log template matches.
- `git diff --check` passed for the touched files.
- `./mvnw -q spotless:check -DskipTests` passed.
- `./mvnw -q -DskipTests compile` passed.

## 2026-06-15 - Service Interface and Validation Text Cleanup

Background:

- Several public service interfaces still had Chinese JavaDoc, sparse parameter descriptions, or
  one-line JavaDoc.
- Gateway, consumer, category, and admin DTOs still had Chinese validation messages.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/service/AdministratorService.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/DeveloperService.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/OidcService.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/IdpService.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/GatewayService.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/OAuth2Service.java`
- `himarket-server/src/main/java/com/alibaba/himarket/service/RevokedTokenService.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/gateway/QueryApsaraGatewayParam.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/consumer/CreateConsumerParam.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/consumer/CreateCredentialParam.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/category/CreateProductCategoryParam.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/category/UpdateProductCategoryParam.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/admin/ResetPasswordParam.java`

Changes:

- Translated service JavaDoc and parameter descriptions to English.
- Expanded touched one-line JavaDoc comments to the preferred multi-line style.
- Translated validation messages to concise English while preserving existing constraints and field
  names.

Compatibility:

- No service signature, DTO field, JSON property, validation annotation, validation bound, or
  authentication/gateway flow changed.

Verification:

- Targeted `rg` scan on the touched files returned no actionable Chinese text or one-line JavaDoc.
- `git diff --check` passed for the touched files.
- `./mvnw -q spotless:check -DskipTests` passed.
- `./mvnw -q -DskipTests compile` passed.

## 2026-06-17 - Controller Request Validation Cleanup

Background:

- The wider Controller/API review found that OpenAPI annotation coverage was already clean, but a
  few request bodies still bypassed Jakarta Validation.
- Commercial `apigw-himarket` uses `@Valid @RequestBody` for the matching developer password and
  status APIs, and validates ID collection elements rather than accepting raw `List<String>` IDs.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/controller/AdministratorController.java`
- `himarket-server/src/main/java/com/alibaba/himarket/controller/DeveloperController.java`
- `himarket-server/src/main/java/com/alibaba/himarket/controller/ProductCategoryController.java`
- `himarket-server/src/main/java/com/alibaba/himarket/controller/ProductController.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/admin/ResetPasswordParam.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/developer/UpdateDeveloperStatusParam.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/product/CreateProductParam.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/product/UpdateProductParam.java`

Changes:

- Added `@Valid` to administrator/developer password update bodies and developer status update
  bodies.
- Added password and developer status field constraints with English validation messages.
- Added element-level `@NotBlank` validation for category/product ID collections in request bodies.

Compatibility:

- No controller path, HTTP method, JSON field name, service call, product/category binding behavior,
  or authentication rule changed.
- The only behavior change is rejecting blank password/status/category/product ID input earlier via
  standard request validation.

Verification:

- Targeted `rg` scan returned no remaining raw `@RequestBody List<String>` category/product ID
  parameters, raw product category DTO `List<String> categories`, or password/status request bodies
  without `@Valid`.
- `git diff --check` passed.
- `./mvnw -q spotless:check -DskipTests` passed.
- `./mvnw -q -DskipTests test-compile` passed.

## 2026-06-17 - Internal Module Dependency Management Cleanup

Background:

- The POM dependency governance pass centralized third-party dependency versions in the parent
  `dependencyManagement`, but internal module dependencies still carried fixed `1.0-SNAPSHOT`
  versions in child modules.
- The commercial backend already manages internal HiMarket module versions from the parent POM
  using `${project.version}`.

Scope:

- `pom.xml`
- `himarket-server/pom.xml`
- `himarket-bootstrap/pom.xml`

Changes:

- Added `himarket-dal` and `himarket-server` to the parent POM `dependencyManagement` with
  `${project.version}`.
- Removed fixed internal module dependency versions from child module POMs.

Compatibility:

- No third-party dependency, plugin, source code, packaging, or runtime behavior changed.
- Internal module resolution remains tied to the current reactor project version.

Verification:

- `git diff --check` passed.
- `./mvnw -q spotless:check -DskipTests` passed.
- `./mvnw -q -DskipTests test-compile` passed.

## 2026-06-17 - Controller Request Validation Follow-up

Background:

- A follow-up review of the completed Controller/API cleanup found a few remaining list inputs that
  validated the collection itself but not blank string elements.
- The commercial backend already validates the matching chat product ID lists with element-level
  `@NotBlank` constraints.

Scope:

- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/chat/CreateChatParam.java`
- `himarket-server/src/main/java/com/alibaba/himarket/dto/params/chat/CreateChatSessionParam.java`
- `himarket-server/src/main/java/com/alibaba/himarket/controller/McpServerController.java`

Changes:

- Added element-level `@NotBlank` validation for chat product ID lists.
- Added method validation to `McpServerController` and element-level `@NotBlank` validation for
  batch product ID request parameters.

Compatibility:

- No endpoint path, request field name, response shape, repository call, or business flow changed.
- The only behavior change is rejecting blank product ID values earlier through standard request
  validation.

Verification:

- `git diff --check` passed.
- `./mvnw -q spotless:check -DskipTests` passed.
- `./mvnw -q -DskipTests test-compile` passed.
