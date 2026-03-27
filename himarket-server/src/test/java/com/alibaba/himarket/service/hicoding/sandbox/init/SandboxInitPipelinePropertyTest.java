package com.alibaba.himarket.service.hicoding.sandbox.init;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.hicoding.filesystem.FileSystemAdapter;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeAdapter;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeConfig;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeStatus;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxConfig;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxInfo;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxProvider;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import net.jqwik.api.*;
import reactor.core.publisher.Flux;

/**
 * SandboxInitPipeline 属性基测试。
 *
 * <p>Feature: sandbox-runtime-strategy
 *
 * <p>验证 Pipeline 的编排行为在各种输入组合下满足正确性属性。
 */
class SandboxInitPipelinePropertyTest {

    // ===== 辅助：Stub SandboxProvider =====

    /** 简单的 stub provider，记录调用但不做实际操作。 */
    static class StubSandboxProvider implements SandboxProvider {
        private final SandboxType type;

        StubSandboxProvider(SandboxType type) {
            this.type = type;
        }

        @Override
        public SandboxType getType() {
            return type;
        }

        @Override
        public SandboxInfo acquire(SandboxConfig config) {
            return new SandboxInfo(
                    type,
                    "stub-" + type.getValue(),
                    "localhost",
                    8080,
                    "/workspace",
                    false,
                    Map.of());
        }

        @Override
        public void release(SandboxInfo info) {}

        @Override
        public boolean healthCheck(SandboxInfo info) {
            return true;
        }

        @Override
        public void writeFile(SandboxInfo info, String relativePath, String content)
                throws IOException {}

        @Override
        public String readFile(SandboxInfo info, String relativePath) throws IOException {
            return "";
        }

        @Override
        public RuntimeAdapter connectSidecar(SandboxInfo info, RuntimeConfig config) {
            return new StubRuntimeAdapter();
        }

        @Override
        public URI getSidecarUri(SandboxInfo info, String command, String args) {
            return URI.create("ws://localhost:8080/?command=" + command);
        }
    }

    /** Stub RuntimeAdapter，模拟运行中的适配器。 */
    static class StubRuntimeAdapter implements RuntimeAdapter {
        @Override
        public SandboxType getType() {
            return SandboxType.REMOTE;
        }

        @Override
        public String start(RuntimeConfig config) {
            return "stub";
        }

        @Override
        public void send(String jsonLine) {}

        @Override
        public Flux<String> stdout() {
            return Flux.empty();
        }

        @Override
        public RuntimeStatus getStatus() {
            return RuntimeStatus.RUNNING;
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public void close() {}

        @Override
        public FileSystemAdapter getFileSystem() {
            return null;
        }
    }

    // ===== 辅助：可配置的 TestPhase =====

    /** 可配置行为的测试阶段。 */
    static class TestPhase implements InitPhase {
        private final String phaseName;
        private final int phaseOrder;
        private final boolean shouldExec;
        private final boolean verifyResult;
        private final RetryPolicy policy;
        private final boolean failOnExecute;
        private final String failMessage;
        private final boolean retryable;
        private final List<String> executionLog;

        TestPhase(
                String phaseName,
                int phaseOrder,
                boolean shouldExec,
                boolean verifyResult,
                RetryPolicy policy,
                boolean failOnExecute,
                String failMessage,
                boolean retryable,
                List<String> executionLog) {
            this.phaseName = phaseName;
            this.phaseOrder = phaseOrder;
            this.shouldExec = shouldExec;
            this.verifyResult = verifyResult;
            this.policy = policy;
            this.failOnExecute = failOnExecute;
            this.failMessage = failMessage;
            this.retryable = retryable;
            this.executionLog = executionLog;
        }

        @Override
        public String name() {
            return phaseName;
        }

        @Override
        public int order() {
            return phaseOrder;
        }

        @Override
        public boolean shouldExecute(InitContext context) {
            return shouldExec;
        }

        @Override
        public void execute(InitContext context) throws InitPhaseException {
            executionLog.add("execute:" + phaseName);
            if (failOnExecute) {
                throw new InitPhaseException(phaseName, failMessage, retryable);
            }
        }

        @Override
        public boolean verify(InitContext context) {
            executionLog.add("verify:" + phaseName);
            return verifyResult;
        }

        @Override
        public RetryPolicy retryPolicy() {
            return policy;
        }
    }

    // ===== 辅助方法 =====

    private InitContext createContext(SandboxProvider provider) {
        CliProviderConfig providerConfig = new CliProviderConfig();
        return new InitContext(provider, "test-user", null, null, providerConfig, null, null);
    }

    // ===== 生成器 =====

    @Provide
    Arbitrary<SandboxType> sandboxTypes() {
        return Arbitraries.of(SandboxType.REMOTE, SandboxType.OPEN_SANDBOX, SandboxType.E2B);
    }

    @Provide
    Arbitrary<List<Integer>> distinctPhaseOrders() {
        return Arbitraries.integers()
                .between(1, 1000)
                .list()
                .ofMinSize(2)
                .ofMaxSize(8)
                .filter(list -> list.stream().distinct().count() == list.size());
    }

    @Provide
    Arbitrary<String> phaseNames() {
        return Arbitraries.of(
                "phase-alpha",
                "phase-beta",
                "phase-gamma",
                "phase-delta",
                "phase-epsilon",
                "phase-zeta",
                "phase-eta",
                "phase-theta");
    }

    @Provide
    Arbitrary<String> errorMessages() {
        return Arbitraries.of(
                "连接超时", "文件系统不可用", "配置注入失败", "Sidecar 未响应", "CLI 启动失败", "权限不足", "网络错误");
    }

    // =========================================================================
    // Property 1: 沙箱类型无关性
    // =========================================================================

    /**
     * <b>Validates: Requirements 4.1, 4.2, 4.3</b>
     *
     * <p>Property 1: 沙箱类型无关性 — 对任意合法初始化配置，Pipeline 的阶段执行顺序和编排行为与 SandboxType
     * 无关，给定相同 InitPhase 列表，不同 Provider 实现的编排行为完全一致。
     */
    @Property(tries = 100)
    void pipeline_orchestrationBehavior_isIndependentOfSandboxType(
            @ForAll("distinctPhaseOrders") List<Integer> orders) {
        // 为每种 SandboxType 创建独立的执行日志
        Map<SandboxType, List<String>> logsByType = new LinkedHashMap<>();
        Map<SandboxType, InitResult> resultsByType = new LinkedHashMap<>();

        for (SandboxType type : SandboxType.values()) {
            List<String> executionLog = new CopyOnWriteArrayList<>();
            List<InitPhase> phases = new ArrayList<>();
            for (int i = 0; i < orders.size(); i++) {
                phases.add(
                        new TestPhase(
                                "phase-" + i,
                                orders.get(i),
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                executionLog));
            }

            StubSandboxProvider provider = new StubSandboxProvider(type);
            InitConfig config = new InitConfig(Duration.ofSeconds(30), true, true, false);
            SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, config);
            InitContext context = createContext(provider);

            InitResult result = pipeline.execute(context);
            logsByType.put(type, executionLog);
            resultsByType.put(type, result);
        }

        // 验证所有类型的执行日志完全一致
        List<String> referenceLog = logsByType.get(SandboxType.REMOTE);
        for (SandboxType type : SandboxType.values()) {
            assertEquals(
                    referenceLog,
                    logsByType.get(type),
                    "SandboxType " + type + " 的执行日志应与 REMOTE 一致");
        }

        // 验证所有类型的结果一致
        boolean referenceSuccess = resultsByType.get(SandboxType.REMOTE).success();
        for (SandboxType type : SandboxType.values()) {
            assertEquals(
                    referenceSuccess,
                    resultsByType.get(type).success(),
                    "SandboxType " + type + " 的成功状态应与 REMOTE 一致");
        }
    }

    /**
     * <b>Validates: Requirements 4.1, 4.2, 4.3</b>
     *
     * <p>Property 1 补充: 含跳过阶段时，不同 SandboxType 的编排行为仍然一致。
     */
    @Property(tries = 100)
    void pipeline_withSkippedPhases_behaviorIsIndependentOfSandboxType(
            @ForAll("distinctPhaseOrders") List<Integer> orders,
            @ForAll @net.jqwik.api.constraints.Size(min = 1, max = 8)
                    List<Boolean> shouldExecuteFlags) {
        // 确保 flags 和 orders 长度一致
        int size = Math.min(orders.size(), shouldExecuteFlags.size());

        Map<SandboxType, List<String>> logsByType = new LinkedHashMap<>();

        for (SandboxType type : SandboxType.values()) {
            List<String> executionLog = new CopyOnWriteArrayList<>();
            List<InitPhase> phases = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                phases.add(
                        new TestPhase(
                                "phase-" + i,
                                orders.get(i),
                                shouldExecuteFlags.get(i),
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                executionLog));
            }

            StubSandboxProvider provider = new StubSandboxProvider(type);
            InitConfig config = new InitConfig(Duration.ofSeconds(30), true, true, false);
            SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, config);
            InitContext context = createContext(provider);

            pipeline.execute(context);
            logsByType.put(type, executionLog);
        }

        List<String> referenceLog = logsByType.get(SandboxType.REMOTE);
        for (SandboxType type : SandboxType.values()) {
            assertEquals(
                    referenceLog,
                    logsByType.get(type),
                    "含跳过阶段时，SandboxType " + type + " 的执行日志应与 REMOTE 一致");
        }
    }

    // =========================================================================
    // Property 3: 阶段执行顺序保证
    // =========================================================================

    /**
     * <b>Validates: Requirements 4.1, 4.2, 4.3, 4.6</b>
     *
     * <p>Property 3: 阶段执行顺序保证 — 各阶段实际执行顺序严格按 order() 升序排列，后续阶段不会在前置阶段 verify()
     * 返回 true 之前被调用。
     */
    @Property(tries = 200)
    void pipeline_executesPhases_inAscendingOrderWithVerifyBeforeNext(
            @ForAll("distinctPhaseOrders") List<Integer> orders) {
        List<String> executionLog = new CopyOnWriteArrayList<>();
        List<InitPhase> phases = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            phases.add(
                    new TestPhase(
                            "phase-" + i,
                            orders.get(i),
                            true,
                            true,
                            RetryPolicy.none(),
                            false,
                            null,
                            false,
                            executionLog));
        }

        InitConfig config = new InitConfig(Duration.ofSeconds(30), true, true, false);
        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, config);
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);
        assertTrue(result.success(), "所有阶段应成功执行");

        // 按 order 排序后的阶段名称
        List<String> sortedPhaseNames =
                phases.stream()
                        .sorted(Comparator.comparingInt(InitPhase::order))
                        .map(InitPhase::name)
                        .toList();

        // 验证执行日志中 execute 的顺序严格按 order 升序
        List<String> executeEntries =
                executionLog.stream().filter(e -> e.startsWith("execute:")).toList();
        List<String> verifyEntries =
                executionLog.stream().filter(e -> e.startsWith("verify:")).toList();

        assertEquals(sortedPhaseNames.size(), executeEntries.size(), "每个阶段应执行一次");
        assertEquals(sortedPhaseNames.size(), verifyEntries.size(), "每个阶段应验证一次");

        for (int i = 0; i < sortedPhaseNames.size(); i++) {
            assertEquals(
                    "execute:" + sortedPhaseNames.get(i),
                    executeEntries.get(i),
                    "第 " + i + " 个执行的阶段应为 " + sortedPhaseNames.get(i));
        }

        // 验证每个阶段的 verify 在下一个阶段的 execute 之前
        for (int i = 0; i < sortedPhaseNames.size() - 1; i++) {
            String currentVerify = "verify:" + sortedPhaseNames.get(i);
            String nextExecute = "execute:" + sortedPhaseNames.get(i + 1);
            int verifyIndex = executionLog.indexOf(currentVerify);
            int nextExecIndex = executionLog.indexOf(nextExecute);
            assertTrue(
                    verifyIndex < nextExecIndex,
                    "阶段 "
                            + sortedPhaseNames.get(i)
                            + " 的 verify 应在阶段 "
                            + sortedPhaseNames.get(i + 1)
                            + " 的 execute 之前");
        }
    }

    /**
     * <b>Validates: Requirements 4.2, 4.6</b>
     *
     * <p>Property 3 补充: shouldExecute() 返回 false 的阶段被跳过并记录 PHASE_SKIP 事件。
     */
    @Property(tries = 200)
    void pipeline_skipsPhases_whenShouldExecuteReturnsFalse(
            @ForAll("distinctPhaseOrders") List<Integer> orders,
            @ForAll @net.jqwik.api.constraints.Size(min = 2, max = 8)
                    List<Boolean> shouldExecuteFlags) {
        int size = Math.min(orders.size(), shouldExecuteFlags.size());
        // 确保至少有一个 false
        boolean hasSkip = shouldExecuteFlags.subList(0, size).contains(false);
        Assume.that(hasSkip);

        List<String> executionLog = new CopyOnWriteArrayList<>();
        List<InitPhase> phases = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            phases.add(
                    new TestPhase(
                            "phase-" + i,
                            orders.get(i),
                            shouldExecuteFlags.get(i),
                            true,
                            RetryPolicy.none(),
                            false,
                            null,
                            false,
                            executionLog));
        }

        InitConfig config = new InitConfig(Duration.ofSeconds(30), true, true, false);
        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, config);
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);
        assertTrue(result.success());

        // 按 order 排序
        List<InitPhase> sortedPhases =
                phases.stream().sorted(Comparator.comparingInt(InitPhase::order)).toList();

        for (int i = 0; i < sortedPhases.size(); i++) {
            InitPhase phase = sortedPhases.get(i);
            boolean shouldExec = phase.shouldExecute(context);

            if (!shouldExec) {
                // 跳过的阶段不应出现在执行日志中
                assertFalse(
                        executionLog.contains("execute:" + phase.name()),
                        "跳过的阶段 " + phase.name() + " 不应被执行");

                // 应记录 PHASE_SKIP 事件
                boolean hasSkipEvent =
                        context.getEvents().stream()
                                .anyMatch(
                                        e ->
                                                e.phase().equals(phase.name())
                                                        && e.type()
                                                                == InitEvent.EventType.PHASE_SKIP);
                assertTrue(hasSkipEvent, "跳过的阶段 " + phase.name() + " 应记录 PHASE_SKIP 事件");

                // 状态应为 SKIPPED
                assertEquals(
                        PhaseStatus.SKIPPED,
                        context.getPhaseStatuses().get(phase.name()),
                        "跳过的阶段 " + phase.name() + " 状态应为 SKIPPED");
            } else {
                // 执行的阶段应出现在日志中
                assertTrue(
                        executionLog.contains("execute:" + phase.name()),
                        "应执行的阶段 " + phase.name() + " 应在执行日志中");
            }
        }
    }

    // =========================================================================
    // Property 4: 失败结果完整性
    // =========================================================================

    /**
     * <b>Validates: Requirements 4.5, 9.4, 9.5</b>
     *
     * <p>Property 4: 失败结果完整性 — 阶段失败时 InitResult 包含失败阶段名称、错误信息、总耗时、各阶段耗时和完整事件日志。
     */
    @Property(tries = 200)
    void pipeline_failureResult_containsCompleteInformation(
            @ForAll("distinctPhaseOrders") List<Integer> orders,
            @ForAll @net.jqwik.api.constraints.IntRange(min = 0, max = 7) int failAtIndex,
            @ForAll("errorMessages") String errorMsg) {
        Assume.that(failAtIndex < orders.size());

        // 按 order 排序后确定哪个阶段失败
        List<Integer> sortedOrders = new ArrayList<>(orders);
        Collections.sort(sortedOrders);

        List<String> executionLog = new CopyOnWriteArrayList<>();
        List<InitPhase> phases = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            boolean shouldFail = (orders.get(i).equals(sortedOrders.get(failAtIndex)));
            phases.add(
                    new TestPhase(
                            "phase-" + i,
                            orders.get(i),
                            true,
                            true,
                            RetryPolicy.none(),
                            shouldFail,
                            errorMsg,
                            false,
                            executionLog));
        }

        InitConfig config = new InitConfig(Duration.ofSeconds(30), true, true, false);
        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, config);
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        // 验证失败结果
        assertFalse(result.success(), "应返回失败结果");

        // 失败阶段名称不为空
        assertNotNull(result.failedPhase(), "failedPhase 不应为 null");
        assertFalse(result.failedPhase().isEmpty(), "failedPhase 不应为空");

        // 错误信息不为空
        assertNotNull(result.errorMessage(), "errorMessage 不应为 null");
        assertEquals(errorMsg, result.errorMessage(), "errorMessage 应与抛出的异常信息一致");

        // 总耗时不为空且非负
        assertNotNull(result.totalDuration(), "totalDuration 不应为 null");
        assertFalse(result.totalDuration().isNegative(), "totalDuration 不应为负");

        // 各阶段耗时不为空
        assertNotNull(result.phaseDurations(), "phaseDurations 不应为 null");
        // 失败阶段应在 phaseDurations 中
        assertTrue(
                result.phaseDurations().containsKey(result.failedPhase()),
                "phaseDurations 应包含失败阶段 " + result.failedPhase());

        // 事件日志不为空
        assertNotNull(result.events(), "events 不应为 null");
        assertFalse(result.events().isEmpty(), "events 不应为空");

        // 应包含 PHASE_FAIL 事件
        boolean hasFailEvent =
                result.events().stream()
                        .anyMatch(
                                e ->
                                        e.phase().equals(result.failedPhase())
                                                && e.type() == InitEvent.EventType.PHASE_FAIL);
        assertTrue(hasFailEvent, "事件日志应包含失败阶段的 PHASE_FAIL 事件");

        // 失败阶段之前的已完成阶段应有 PHASE_COMPLETE 事件
        for (Map.Entry<String, Duration> entry : result.phaseDurations().entrySet()) {
            if (!entry.getKey().equals(result.failedPhase())) {
                boolean hasCompleteEvent =
                        result.events().stream()
                                .anyMatch(
                                        e ->
                                                e.phase().equals(entry.getKey())
                                                        && e.type()
                                                                == InitEvent.EventType
                                                                        .PHASE_COMPLETE);
                assertTrue(hasCompleteEvent, "已完成阶段 " + entry.getKey() + " 应有 PHASE_COMPLETE 事件");
            }
        }
    }

    /**
     * <b>Validates: Requirements 4.5, 9.4</b>
     *
     * <p>Property 4 补充: 失败阶段之后的阶段不应被执行。
     */
    @Property(tries = 200)
    void pipeline_afterFailure_subsequentPhasesAreNotExecuted(
            @ForAll("distinctPhaseOrders") List<Integer> orders,
            @ForAll @net.jqwik.api.constraints.IntRange(min = 0, max = 7) int failAtIndex,
            @ForAll("errorMessages") String errorMsg) {
        Assume.that(failAtIndex < orders.size());

        List<Integer> sortedOrders = new ArrayList<>(orders);
        Collections.sort(sortedOrders);
        int failOrder = sortedOrders.get(failAtIndex);

        List<String> executionLog = new CopyOnWriteArrayList<>();
        List<InitPhase> phases = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            boolean shouldFail = (orders.get(i) == failOrder);
            phases.add(
                    new TestPhase(
                            "phase-" + i,
                            orders.get(i),
                            true,
                            true,
                            RetryPolicy.none(),
                            shouldFail,
                            errorMsg,
                            false,
                            executionLog));
        }

        InitConfig config = new InitConfig(Duration.ofSeconds(30), true, true, false);
        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, config);
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);
        assertFalse(result.success());

        // 失败阶段之后的阶段不应被执行
        for (InitPhase phase : phases) {
            if (phase.order() > failOrder) {
                assertFalse(
                        executionLog.contains("execute:" + phase.name()),
                        "失败阶段之后的阶段 " + phase.name() + " 不应被执行");
            }
        }
    }

    // =========================================================================
    // Property 6: 超时保证
    // =========================================================================

    /**
     * <b>Validates: Requirements 4.7</b>
     *
     * <p>Property 6: 超时保证 — 总耗时不超过 InitConfig.totalTimeout + 5s 清理时间。
     */
    @Property(tries = 50)
    void pipeline_totalDuration_doesNotExceedTimeoutPlusCleanup(
            @ForAll @net.jqwik.api.constraints.IntRange(min = 1, max = 3) int timeoutSeconds) {
        Duration totalTimeout = Duration.ofSeconds(timeoutSeconds);
        Duration maxAllowed = totalTimeout.plusSeconds(5);

        // 创建一个会阻塞超过 timeout 的阶段
        List<String> executionLog = new CopyOnWriteArrayList<>();
        InitPhase slowPhase =
                new InitPhase() {
                    @Override
                    public String name() {
                        return "slow-phase";
                    }

                    @Override
                    public int order() {
                        return 100;
                    }

                    @Override
                    public boolean shouldExecute(InitContext context) {
                        return true;
                    }

                    @Override
                    public void execute(InitContext context) throws InitPhaseException {
                        executionLog.add("execute:slow-phase");
                        // 模拟耗时操作，但不会无限阻塞
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    @Override
                    public boolean verify(InitContext context) {
                        return true;
                    }

                    @Override
                    public RetryPolicy retryPolicy() {
                        return RetryPolicy.none();
                    }
                };

        // 创建多个阶段，总执行时间会超过 timeout
        List<InitPhase> phases = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int idx = i;
            phases.add(
                    new InitPhase() {
                        @Override
                        public String name() {
                            return "phase-" + idx;
                        }

                        @Override
                        public int order() {
                            return 100 + idx;
                        }

                        @Override
                        public boolean shouldExecute(InitContext context) {
                            return true;
                        }

                        @Override
                        public void execute(InitContext context) throws InitPhaseException {
                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }

                        @Override
                        public boolean verify(InitContext context) {
                            return true;
                        }

                        @Override
                        public RetryPolicy retryPolicy() {
                            return RetryPolicy.none();
                        }
                    });
        }

        InitConfig config = new InitConfig(totalTimeout, true, true, false);
        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, config);
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        long startMs = System.currentTimeMillis();
        InitResult result = pipeline.execute(context);
        long actualMs = System.currentTimeMillis() - startMs;
        Duration actualDuration = Duration.ofMillis(actualMs);

        // 总耗时不应超过 timeout + 5s 清理时间
        assertTrue(
                actualDuration.compareTo(maxAllowed) <= 0,
                "总耗时 "
                        + actualDuration.toMillis()
                        + "ms 不应超过 "
                        + maxAllowed.toMillis()
                        + "ms (timeout + 5s)");

        // 如果超时了，结果应为失败
        if (!result.success()) {
            assertNotNull(result.failedPhase(), "超时失败时 failedPhase 不应为 null");
            assertNotNull(result.totalDuration(), "超时失败时 totalDuration 不应为 null");
        }
    }

    /**
     * <b>Validates: Requirements 4.7</b>
     *
     * <p>Property 6 补充: 超时时 Pipeline 返回失败结果并包含超时信息。
     */
    @Property(tries = 30)
    void pipeline_onTimeout_returnsFailureWithTimeoutInfo(
            @ForAll @net.jqwik.api.constraints.IntRange(min = 1, max = 2) int timeoutSeconds) {
        Duration totalTimeout = Duration.ofSeconds(timeoutSeconds);

        // 第一个阶段正常，第二个阶段会导致超时检查触发
        List<InitPhase> phases = new ArrayList<>();
        // 第一个阶段耗时接近 timeout
        phases.add(
                new InitPhase() {
                    @Override
                    public String name() {
                        return "long-phase";
                    }

                    @Override
                    public int order() {
                        return 100;
                    }

                    @Override
                    public boolean shouldExecute(InitContext context) {
                        return true;
                    }

                    @Override
                    public void execute(InitContext context) throws InitPhaseException {
                        try {
                            // 睡眠超过 timeout，使得下一个阶段开始前超时检查触发
                            Thread.sleep(totalTimeout.toMillis() + 200);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    @Override
                    public boolean verify(InitContext context) {
                        return true;
                    }

                    @Override
                    public RetryPolicy retryPolicy() {
                        return RetryPolicy.none();
                    }
                });
        // 第二个阶段 — 超时检查在此阶段开始前触发
        phases.add(
                new TestPhase(
                        "after-timeout-phase",
                        200,
                        true,
                        true,
                        RetryPolicy.none(),
                        false,
                        null,
                        false,
                        new CopyOnWriteArrayList<>()));

        InitConfig config = new InitConfig(totalTimeout, true, true, false);
        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, config);
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertFalse(result.success(), "超时后应返回失败结果");
        assertNotNull(result.failedPhase(), "超时失败时 failedPhase 不应为 null");
        assertNotNull(result.errorMessage(), "超时失败时 errorMessage 不应为 null");
        assertTrue(
                result.errorMessage().contains("超时"), "超时错误信息应包含'超时'关键字: " + result.errorMessage());
        assertNotNull(result.totalDuration(), "超时失败时 totalDuration 不应为 null");
        assertNotNull(result.events(), "超时失败时 events 不应为 null");
        assertFalse(result.events().isEmpty(), "超时失败时 events 不应为空");
    }
}
