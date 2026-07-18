package org.churchband.web;

import java.time.LocalDate;

import org.churchband.service.LiveSolveService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Endpoints for a live, streaming solve (as opposed to RosterController's
 * blocking POST /api/solve).
 *
 * POST /api/solve/start          - kick off a solve, get back a session id
 * GET  /api/solve/stream/{id}    - open an SSE connection for live updates
 * POST /api/solve/stop/{id}      - request early termination
 *
 * Usage from the frontend:
 *   1. POST /api/solve/start with {startDate, numberOfWeeks, solveTimeSeconds}
 *   2. Immediately open new EventSource("/api/solve/stream/" + sessionId)
 *   3. Listen for "update" events (live progress) and "done" (final result)
 *   4. Optionally POST /api/solve/stop/{sessionId} to interrupt
 */
@RestController
@RequestMapping("/api/solve")
public class LiveSolveController {

    private final LiveSolveService liveSolveService;

    public LiveSolveController(LiveSolveService liveSolveService) {
        this.liveSolveService = liveSolveService;
    }

    @PostMapping("/start")
    public StartResponse start(@RequestBody StartRequest request) {
        int solveTimeSeconds = request.solveTimeSeconds() != null ? request.solveTimeSeconds() : 20;
        String sessionId = liveSolveService.startSolve(request.startDate(), request.numberOfWeeks(), solveTimeSeconds);
        return new StartResponse(sessionId);
    }

    // NOTE: EventSource (the browser API for SSE) cannot send an
    // Authorization header, so this endpoint can't use the same HTTP
    // Basic auth as the rest of /api/**. See SecurityConfig - this path
    // is carved out to allow unauthenticated GET access to the stream
    // itself. The session id is an unguessable UUID, and starting a
    // solve (POST /api/solve/start) still requires auth, so this is a
    // narrow, deliberate exception rather than an open door.
    @GetMapping("/stream/{sessionId}")
    public SseEmitter stream(@PathVariable String sessionId) {
        return liveSolveService.subscribe(sessionId);
    }

    @PostMapping("/stop/{sessionId}")
    public StopResponse stop(@PathVariable String sessionId) {
        boolean stopped = liveSolveService.stop(sessionId);
        return new StopResponse(stopped);
    }

    public record StartRequest(LocalDate startDate, int numberOfWeeks, Integer solveTimeSeconds) {
    }

    public record StartResponse(String sessionId) {
    }

    public record StopResponse(boolean stopped) {
    }
}