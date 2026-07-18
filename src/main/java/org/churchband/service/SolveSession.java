package org.churchband.service;

import org.churchband.domain.Schedule;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ai.timefold.solver.core.api.solver.Solver;

/**
 * Tracks one live-solve session: the running Solver (so it can be
 * stopped early), the SSE connection currently subscribed to its
 * updates, and whether it has finished.
 *
 * One session = one call to LiveSolveService.startSolve(...). The
 * session's id is what the frontend uses to open the SSE stream and
 * to request an early stop.
 */
public class SolveSession {

    private final String id;
    private volatile Solver<Schedule> solver;
    private volatile SseEmitter emitter;
    private volatile boolean finished = false;

    public SolveSession(String id) {
        this.id = id;
    }

    public String getId() { return id; }

    public Solver<Schedule> getSolver() { return solver; }
    public void setSolver(Solver<Schedule> solver) { this.solver = solver; }

    public SseEmitter getEmitter() { return emitter; }
    public void setEmitter(SseEmitter emitter) { this.emitter = emitter; }

    public boolean isFinished() { return finished; }
    public void markFinished() { this.finished = true; }
}