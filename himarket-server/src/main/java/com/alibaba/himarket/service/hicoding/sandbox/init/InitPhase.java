package com.alibaba.himarket.service.hicoding.sandbox.init;

/**
 * Initialization phase interface.
 *
 * <p>Each phase obtains the {@link com.alibaba.himarket.service.hicoding.sandbox.SandboxProvider}
 * through {@link InitContext#getProvider()} and performs sandbox-type-agnostic initialization
 * logic.
 */
public interface InitPhase {

    /**
     * Phase name used for logs and event tracing.
     */
    String name();

    /**
     * Execution order; smaller values run earlier.
     */
    int order();

    /**
     * Returns whether the current phase should execute.
     *
     * <p>When false, the pipeline skips the phase and records a PHASE_SKIP event.
     */
    boolean shouldExecute(InitContext context);

    /**
     * Executes phase logic.
     */
    void execute(InitContext context) throws InitPhaseException;

    /**
     * Verifies whether the phase result is ready.
     *
     * <p>The next phase runs only after verify returns true.
     */
    boolean verify(InitContext context);

    /**
     * Retry policy for this phase.
     */
    RetryPolicy retryPolicy();
}
