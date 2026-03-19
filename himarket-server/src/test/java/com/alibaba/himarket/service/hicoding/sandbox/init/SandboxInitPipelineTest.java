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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * SandboxInitPipeline 单元测试。
 *
 * <p>验证 Pipeline 的阶段顺序执行、跳过逻辑、失败快速退出、重试、超时终止和 resumeFrom 恢复执行。
 *
 * <p><b>Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 4.7, 4.8</b>
 */
class SandboxInitPipelineTest {

    // ===== 辅助：Stub SandboxProvider =====

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

    // ===== 辅助：Stub RuntimeAdapter =====

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

    // ===== 辅助：支持第 N 次成功的 TestPhase =====

    static class RetryableTestPhase implements InitPhase {
        private final String phaseName;
        private final int phaseOrder;
        private final RetryPolicy policy;
        private final int succeedOnAttempt;
        private final AtomicInteger attemptCount = new AtomicInteger(0);
        private final List<String> executionLog;

        RetryableTestPhase(
                String phaseName,
                int phaseOrder,
                RetryPolicy policy,
                int succeedOnAttempt,
                List<String> executionLog) {
            this.phaseName = phaseName;
            this.phaseOrder = phaseOrder;
            this.policy = policy;
            this.succeedOnAttempt = succeedOnAttempt;
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
            return true;
        }

        @Override
        public void execute(InitContext context) throws InitPhaseException {
            int attempt = attemptCount.incrementAndGet();
            executionLog.add("execute:" + phaseName + ":attempt-" + attempt);
            if (attempt < succeedOnAttempt) {
                throw new InitPhaseException(phaseName, "第 " + attempt + " 次失败", true);
            }
        }

        @Override
        public boolean verify(InitContext context) {
            executionLog.add("verify:" + phaseName);
            return true;
        }

        @Override
        public RetryPolicy retryPolicy() {
            return policy;
        }

        public int getAttemptCount() {
            return attemptCount.get();
        }
    }

    // ===== 辅助方法 =====

    private InitContext createContext(SandboxProvider provider) {
        CliProviderConfig providerConfig = new CliProviderConfig();
        return new InitContext(provider, "test-user", null, null, providerConfig, null, null);
    }

    private InitConfig defaultConfig() {
        return new InitConfig(Duration.ofSeconds(30), true, true, false);
    }

    // =========================================================================
    // 测试：阶段顺序执行 (Requirements 4.1)
    // =========================================================================

    @Test
    void execute_phasesRunInAscendingOrder() {
        List<String> log = new CopyOnWriteArrayList<>();
        List<InitPhase> phases =
                List.of(
                        new TestPhase(
                                "phase-c",
                                300,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log),
                        new TestPhase(
                                "phase-a",
                                100,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log),
                        new TestPhase(
                                "phase-b",
                                200,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log));

        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertTrue(result.success());
        // 验证执行顺序为 a -> b -> c
        List<String> execEntries = log.stream().filter(e -> e.startsWith("execute:")).toList();
        assertEquals(List.of("execute:phase-a", "execute:phase-b", "execute:phase-c"), execEntries);
    }

    @Test
    void execute_verifyCalledAfterExecuteBeforeNextPhase() {
        List<String> log = new CopyOnWriteArrayList<>();
        List<InitPhase> phases =
                List.of(
                        new TestPhase(
                                "phase-1",
                                100,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log),
                        new TestPhase(
                                "phase-2",
                                200,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log));

        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertTrue(result.success());
        // 期望顺序: execute:1 -> verify:1 -> execute:2 -> verify:2
        assertEquals(
                List.of("execute:phase-1", "verify:phase-1", "execute:phase-2", "verify:phase-2"),
                log);
    }

    @Test
    void execute_recordsPhaseStartAndCompleteEvents() {
        List<String> log = new CopyOnWriteArrayList<>();
        List<InitPhase> phases =
                List.of(
                        new TestPhase(
                                "phase-1",
                                100,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log));

        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertTrue(result.success());
        // 验证事件记录
        boolean hasStart =
                context.getEvents().stream()
                        .anyMatch(
                                e ->
                                        e.phase().equals("phase-1")
                                                && e.type() == InitEvent.EventType.PHASE_START);
        boolean hasComplete =
                context.getEvents().stream()
                        .anyMatch(
                                e ->
                                        e.phase().equals("phase-1")
                                                && e.type() == InitEvent.EventType.PHASE_COMPLETE);
        assertTrue(hasStart, "应记录 PHASE_START 事件");
        assertTrue(hasComplete, "应记录 PHASE_COMPLETE 事件");
    }

    @Test
    void execute_resultContainsPhaseDurations() {
        List<String> log = new CopyOnWriteArrayList<>();
        List<InitPhase> phases =
                List.of(
                        new TestPhase(
                                "phase-1",
                                100,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log),
                        new TestPhase(
                                "phase-2",
                                200,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log));

        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertTrue(result.success());
        assertNotNull(result.phaseDurations());
        assertTrue(result.phaseDurations().containsKey("phase-1"));
        assertTrue(result.phaseDurations().containsKey("phase-2"));
        assertNotNull(result.totalDuration());
        assertFalse(result.totalDuration().isNegative());
    }

    // =========================================================================
    // 测试：shouldExecute 返回 false 时跳过阶段 (Requirements 4.2)
    // =========================================================================

    @Test
    void execute_skipsPhaseWhenShouldExecuteReturnsFalse() {
        List<String> log = new CopyOnWriteArrayList<>();
        List<InitPhase> phases =
                List.of(
                        new TestPhase(
                                "phase-1",
                                100,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log),
                        new TestPhase(
                                "skipped",
                                200,
                                false,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log),
                        new TestPhase(
                                "phase-3",
                                300,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log));

        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertTrue(result.success());
        // 跳过的阶段不应出现在执行日志中
        assertFalse(log.contains("execute:skipped"));
        // 其他阶段正常执行
        assertTrue(log.contains("execute:phase-1"));
        assertTrue(log.contains("execute:phase-3"));
    }

    @Test
    void execute_recordsPhaseSkipEvent() {
        List<String> log = new CopyOnWriteArrayList<>();
        List<InitPhase> phases =
                List.of(
                        new TestPhase(
                                "skipped",
                                100,
                                false,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log));

        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertTrue(result.success());
        boolean hasSkipEvent =
                context.getEvents().stream()
                        .anyMatch(
                                e ->
                                        e.phase().equals("skipped")
                                                && e.type() == InitEvent.EventType.PHASE_SKIP);
        assertTrue(hasSkipEvent, "应记录 PHASE_SKIP 事件");
        assertEquals(PhaseStatus.SKIPPED, context.getPhaseStatuses().get("skipped"));
    }

    @Test
    void execute_allPhasesSkipped_returnsSuccess() {
        List<String> log = new CopyOnWriteArrayList<>();
        List<InitPhase> phases =
                List.of(
                        new TestPhase(
                                "skip-1",
                                100,
                                false,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log),
                        new TestPhase(
                                "skip-2",
                                200,
                                false,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log));

        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertTrue(result.success());
        assertTrue(log.isEmpty(), "跳过的阶段不应有执行日志");
    }

    // =========================================================================
    // 测试：阶段失败后快速退出 (Requirements 4.5)
    // =========================================================================

    @Test
    void execute_failsImmediatelyWhenPhaseThrows() {
        List<String> log = new CopyOnWriteArrayList<>();
        List<InitPhase> phases =
                List.of(
                        new TestPhase(
                                "phase-1",
                                100,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log),
                        new TestPhase(
                                "fail-phase",
                                200,
                                true,
                                true,
                                RetryPolicy.none(),
                                true,
                                "模拟失败",
                                false,
                                log),
                        new TestPhase(
                                "phase-3",
                                300,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log));

        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertFalse(result.success());
        assertEquals("fail-phase", result.failedPhase());
        assertEquals("模拟失败", result.errorMessage());
        // 失败阶段之后的阶段不应被执行
        assertFalse(log.contains("execute:phase-3"));
        // 失败阶段之前的阶段应已执行
        assertTrue(log.contains("execute:phase-1"));
    }

    @Test
    void execute_failureResultContainsPhaseFailEvent() {
        List<String> log = new CopyOnWriteArrayList<>();
        List<InitPhase> phases =
                List.of(
                        new TestPhase(
                                "fail-phase",
                                100,
                                true,
                                true,
                                RetryPolicy.none(),
                                true,
                                "出错了",
                                false,
                                log));

        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertFalse(result.success());
        boolean hasFailEvent =
                result.events().stream()
                        .anyMatch(
                                e ->
                                        e.phase().equals("fail-phase")
                                                && e.type() == InitEvent.EventType.PHASE_FAIL);
        assertTrue(hasFailEvent, "应记录 PHASE_FAIL 事件");
        assertEquals(PhaseStatus.FAILED, context.getPhaseStatuses().get("fail-phase"));
    }

    @Test
    void execute_failureResultContainsDurationsForCompletedPhases() {
        List<String> log = new CopyOnWriteArrayList<>();
        List<InitPhase> phases =
                List.of(
                        new TestPhase(
                                "ok-phase",
                                100,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log),
                        new TestPhase(
                                "fail-phase",
                                200,
                                true,
                                true,
                                RetryPolicy.none(),
                                true,
                                "失败",
                                false,
                                log));

        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertFalse(result.success());
        assertTrue(result.phaseDurations().containsKey("ok-phase"));
        assertTrue(result.phaseDurations().containsKey("fail-phase"));
        assertNotNull(result.totalDuration());
    }

    @Test
    void execute_verifyFailureCausesFailResult() {
        List<String> log = new CopyOnWriteArrayList<>();
        // verify 返回 false
        List<InitPhase> phases =
                List.of(
                        new TestPhase(
                                "bad-verify",
                                100,
                                true,
                                false,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log));

        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertFalse(result.success());
        assertEquals("bad-verify", result.failedPhase());
        assertTrue(result.errorMessage().contains("验证失败"));
    }

    // =========================================================================
    // 测试：重试逻辑（第 N 次成功）(Requirements 4.4)
    // =========================================================================

    @Test
    void execute_retriesAndSucceedsOnSecondAttempt() {
        List<String> log = new CopyOnWriteArrayList<>();
        RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(10), 1.0, Duration.ofMillis(50));
        RetryableTestPhase retryPhase = new RetryableTestPhase("retry-phase", 100, policy, 2, log);

        SandboxInitPipeline pipeline =
                new SandboxInitPipeline(List.of(retryPhase), defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertTrue(result.success());
        assertEquals(2, retryPhase.getAttemptCount());
        assertTrue(log.contains("execute:retry-phase:attempt-1"));
        assertTrue(log.contains("execute:retry-phase:attempt-2"));
    }

    @Test
    void execute_retriesAndSucceedsOnThirdAttempt() {
        List<String> log = new CopyOnWriteArrayList<>();
        RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(10), 1.0, Duration.ofMillis(50));
        RetryableTestPhase retryPhase = new RetryableTestPhase("retry-phase", 100, policy, 3, log);

        SandboxInitPipeline pipeline =
                new SandboxInitPipeline(List.of(retryPhase), defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertTrue(result.success());
        assertEquals(3, retryPhase.getAttemptCount());
    }

    @Test
    void execute_failsAfterExhaustingRetries() {
        List<String> log = new CopyOnWriteArrayList<>();
        RetryPolicy policy = new RetryPolicy(2, Duration.ofMillis(10), 1.0, Duration.ofMillis(50));
        // 需要第 10 次才成功，但只允许 2 次重试（共 3 次尝试）
        RetryableTestPhase retryPhase = new RetryableTestPhase("retry-phase", 100, policy, 10, log);

        SandboxInitPipeline pipeline =
                new SandboxInitPipeline(List.of(retryPhase), defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertFalse(result.success());
        assertEquals("retry-phase", result.failedPhase());
        assertEquals(3, retryPhase.getAttemptCount()); // 1 初始 + 2 重试
    }

    @Test
    void execute_retryRecordsPhaseRetryEvents() {
        List<String> log = new CopyOnWriteArrayList<>();
        RetryPolicy policy = new RetryPolicy(2, Duration.ofMillis(10), 1.0, Duration.ofMillis(50));
        RetryableTestPhase retryPhase = new RetryableTestPhase("retry-phase", 100, policy, 2, log);

        SandboxInitPipeline pipeline =
                new SandboxInitPipeline(List.of(retryPhase), defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertTrue(result.success());
        boolean hasRetryEvent =
                context.getEvents().stream()
                        .anyMatch(
                                e ->
                                        e.phase().equals("retry-phase")
                                                && e.type() == InitEvent.EventType.PHASE_RETRY);
        assertTrue(hasRetryEvent, "应记录 PHASE_RETRY 事件");
    }

    @Test
    void execute_nonRetryableExceptionDoesNotRetry() {
        List<String> log = new CopyOnWriteArrayList<>();
        // retryable=false，即使有重试策略也不重试
        List<InitPhase> phases =
                List.of(
                        new TestPhase(
                                "no-retry",
                                100,
                                true,
                                true,
                                new RetryPolicy(
                                        3, Duration.ofMillis(10), 1.0, Duration.ofMillis(50)),
                                true,
                                "不可重试错误",
                                false,
                                log));

        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertFalse(result.success());
        // 只执行了一次
        long execCount = log.stream().filter(e -> e.equals("execute:no-retry")).count();
        assertEquals(1, execCount);
    }

    @Test
    void execute_retryPolicyNone_doesNotRetry() {
        List<String> log = new CopyOnWriteArrayList<>();
        List<InitPhase> phases =
                List.of(
                        new TestPhase(
                                "no-retry",
                                100,
                                true,
                                true,
                                RetryPolicy.none(),
                                true,
                                "直接失败",
                                true,
                                log));

        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertFalse(result.success());
        long execCount = log.stream().filter(e -> e.equals("execute:no-retry")).count();
        assertEquals(1, execCount);
    }

    // =========================================================================
    // 测试：总超时终止 (Requirements 4.7)
    // =========================================================================

    @Test
    void execute_terminatesWhenTotalTimeoutExceeded() {
        Duration totalTimeout = Duration.ofSeconds(1);
        InitConfig config = new InitConfig(totalTimeout, true, true, false);

        // 第一个阶段耗时超过 timeout
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
                        try {
                            Thread.sleep(1500);
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

        List<String> log = new CopyOnWriteArrayList<>();
        TestPhase afterPhase =
                new TestPhase(
                        "after-timeout",
                        200,
                        true,
                        true,
                        RetryPolicy.none(),
                        false,
                        null,
                        false,
                        log);

        SandboxInitPipeline pipeline =
                new SandboxInitPipeline(List.of(slowPhase, afterPhase), config);
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        // 超时后第二个阶段不应被执行
        assertFalse(result.success());
        assertNotNull(result.failedPhase());
        assertTrue(result.errorMessage().contains("超时"));
        assertFalse(log.contains("execute:after-timeout"));
    }

    @Test
    void execute_timeoutResultContainsFailPhaseInfo() {
        Duration totalTimeout = Duration.ofMillis(100);
        InitConfig config = new InitConfig(totalTimeout, true, true, false);

        InitPhase slowPhase =
                new InitPhase() {
                    @Override
                    public String name() {
                        return "slow";
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
                };

        List<String> log = new CopyOnWriteArrayList<>();
        TestPhase nextPhase =
                new TestPhase("next", 200, true, true, RetryPolicy.none(), false, null, false, log);

        SandboxInitPipeline pipeline =
                new SandboxInitPipeline(List.of(slowPhase, nextPhase), config);
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertFalse(result.success());
        assertNotNull(result.totalDuration());
        assertNotNull(result.events());
        assertFalse(result.events().isEmpty());
    }

    // =========================================================================
    // 测试：resumeFrom 恢复执行 (Requirements 4.8)
    // =========================================================================

    @Test
    void resumeFrom_executesFromSpecifiedPhase() {
        List<String> log = new CopyOnWriteArrayList<>();
        List<InitPhase> phases =
                List.of(
                        new TestPhase(
                                "phase-1",
                                100,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log),
                        new TestPhase(
                                "phase-2",
                                200,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log),
                        new TestPhase(
                                "phase-3",
                                300,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log));

        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.resumeFrom(context, "phase-2");

        assertTrue(result.success());
        // phase-1 不应被执行
        assertFalse(log.contains("execute:phase-1"));
        // phase-2 和 phase-3 应被执行
        assertTrue(log.contains("execute:phase-2"));
        assertTrue(log.contains("execute:phase-3"));
    }

    @Test
    void resumeFrom_lastPhase_executesOnlyThatPhase() {
        List<String> log = new CopyOnWriteArrayList<>();
        List<InitPhase> phases =
                List.of(
                        new TestPhase(
                                "phase-1",
                                100,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log),
                        new TestPhase(
                                "phase-2",
                                200,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log));

        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.resumeFrom(context, "phase-2");

        assertTrue(result.success());
        assertFalse(log.contains("execute:phase-1"));
        assertTrue(log.contains("execute:phase-2"));
    }

    @Test
    void resumeFrom_unknownPhase_executesFromBeginning() {
        List<String> log = new CopyOnWriteArrayList<>();
        List<InitPhase> phases =
                List.of(
                        new TestPhase(
                                "phase-1",
                                100,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log),
                        new TestPhase(
                                "phase-2",
                                200,
                                true,
                                true,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log));

        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        // 不存在的阶段名，应从头开始
        InitResult result = pipeline.resumeFrom(context, "nonexistent");

        assertTrue(result.success());
        assertTrue(log.contains("execute:phase-1"));
        assertTrue(log.contains("execute:phase-2"));
    }

    // =========================================================================
    // 测试：空阶段列表
    // =========================================================================

    @Test
    void execute_emptyPhaseList_returnsSuccess() {
        SandboxInitPipeline pipeline = new SandboxInitPipeline(List.of(), defaultConfig());
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        assertTrue(result.success());
        assertNotNull(result.totalDuration());
        assertTrue(result.phaseDurations().isEmpty());
    }

    // =========================================================================
    // 测试：验证禁用时跳过 verify (Requirements 4.3)
    // =========================================================================

    @Test
    void execute_skipsVerifyWhenVerificationDisabled() {
        List<String> log = new CopyOnWriteArrayList<>();
        // verify 返回 false，但 enableVerification=false
        InitConfig config = new InitConfig(Duration.ofSeconds(30), true, false, false);
        List<InitPhase> phases =
                List.of(
                        new TestPhase(
                                "phase-1",
                                100,
                                true,
                                false,
                                RetryPolicy.none(),
                                false,
                                null,
                                false,
                                log));

        SandboxInitPipeline pipeline = new SandboxInitPipeline(phases, config);
        InitContext context = createContext(new StubSandboxProvider(SandboxType.REMOTE));

        InitResult result = pipeline.execute(context);

        // 即使 verify 返回 false，禁用验证后仍应成功
        assertTrue(result.success());
        assertTrue(log.contains("execute:phase-1"));
        // verify 不应被调用
        assertFalse(log.contains("verify:phase-1"));
    }
}
