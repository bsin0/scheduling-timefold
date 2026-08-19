package org.churchband.service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.churchband.domain.Assignment;
import org.churchband.domain.ConstraintWeights;
import org.churchband.domain.Musician;
import org.churchband.domain.PairPreference;
import org.churchband.domain.Role;
import org.churchband.domain.RosterRoles;
import org.churchband.domain.Schedule;
import org.churchband.domain.ScheduleConstraintProvider;
import org.churchband.domain.SundayService;
import org.churchband.persistence.WeightEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.event.BestSolutionChangedEvent;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;

/**
 * Runs Timefold solves asynchronously (on a background thread) and
 * streams progress to the browser over Server-Sent Events, instead of
 * blocking the HTTP request for the whole solve like RosterService.solve()
 * does.
 *
 * Flow:
 *   1. startSolve(...) creates a SolveSession, launches solving on a
 *      background thread, returns the session id immediately.
 *   2. The browser opens GET /api/solve/stream/{id}, which calls
 *      subscribe(id) to get an SseEmitter and attach it to the session.
 *   3. A BestSolutionChangedEventListener registered on the Solver fires
 *      every time Timefold finds a better solution; each firing pushes
 *      a JSON snapshot (score + roster) through the emitter.
 *   4. When solving finishes (time limit reached or stopped early), a
 *      final "done" event is sent and the emitter completes.
 *
 * Sessions are kept in memory only (ConcurrentHashMap) — fine for a
 * single-admin tool where solves are one-at-a-time and short-lived.
 */
@Service
public class LiveSolveService {

    private static final Logger log = LoggerFactory.getLogger(LiveSolveService.class);

    private final WeightService weightService;
    private final RosterService rosterService;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, SolveSession> sessions = new ConcurrentHashMap<>();

    public LiveSolveService(WeightService weightService, RosterService rosterService) {
        this.weightService = weightService;
        this.rosterService = rosterService;
    }

    /**
     * Starts a new solve on a background thread and returns its session
     * id immediately (does NOT block for the solve to finish).
     */
    public String startSolve(LocalDate startDate, int numberOfWeeks, int solveTimeSeconds) {
        String sessionId = UUID.randomUUID().toString();
        SolveSession session = new SolveSession(sessionId);
        sessions.put(sessionId, session);

        executor.submit(() -> runSolve(session, startDate, numberOfWeeks, solveTimeSeconds));

        return sessionId;
    }

    /**
     * Attaches an SSE connection to an existing session so the browser
     * starts receiving updates. If the solve already finished before the
     * browser connected (unlikely given how fast this call returns, but
     * possible on a very short solve time), sends the final state
     * immediately instead of leaving the connection hanging.
     */
    public SseEmitter subscribe(String sessionId) {
        SolveSession session = sessions.get(sessionId);
        if (session == null) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.completeWithError(new IllegalArgumentException("Unknown solve session: " + sessionId));
            return emitter;
        }

        SseEmitter emitter = new SseEmitter(0L); // no timeout - solve duration controls this
        session.setEmitter(emitter);
        emitter.onCompletion(() -> sessions.remove(sessionId));
        emitter.onTimeout(() -> sessions.remove(sessionId));
        return emitter;
    }

    /** Requests early termination of a running solve. No-op if already finished or unknown. */
    public boolean stop(String sessionId) {
        SolveSession session = sessions.get(sessionId);
        if (session == null || session.getSolver() == null) {
            return false;
        }
        session.getSolver().terminateEarly();
        return true;
    }

    private void runSolve(SolveSession session, LocalDate startDate, int numberOfWeeks, int solveTimeSeconds) {
        try {
            Map<String, Integer> weights = weightService.listAll().stream()
                    .collect(Collectors.toMap(WeightEntity::getName, WeightEntity::getValue));
            ConstraintWeights.set(weights);

            Schedule unsolvedSchedule = buildUnsolvedSchedule(startDate, numberOfWeeks);

            SolverConfig solverConfig = new SolverConfig()
                    .withSolutionClass(Schedule.class)
                    .withEntityClasses(Assignment.class)
                    .withConstraintProviderClass(ScheduleConstraintProvider.class)
                    .withTerminationSpentLimit(Duration.ofSeconds(solveTimeSeconds))
                    .withEnvironmentMode(EnvironmentMode.FULL_ASSERT);

            SolverFactory<Schedule> solverFactory = SolverFactory.create(solverConfig);
            Solver<Schedule> solver = solverFactory.buildSolver();
            session.setSolver(solver);

            solver.addEventListener(event -> onBestSolutionChanged(session, event));

            Schedule solved = solver.solve(unsolvedSchedule);
            rosterService.registerLastSolvedSchedule(solved);
            sendEvent(session, "done", toSnapshot(solved));
        } catch (Exception e) {
            // FIX: this catch previously only wrapped solver.solve(...).
            // Any exception thrown while building the schedule or
            // configuring the solver (e.g. a database error, a bad
            // entity mapping) was thrown on this background executor
            // thread and never caught anywhere — the thread would just
            // die silently. No "error" SSE event was ever sent, so the
            // browser's EventSource (and curl, in testing) would sit
            // connected forever with no data, which is exactly the
            // symptom that was reported: the connection opens fine but
            // nothing ever streams, even well past the solve time limit.
            // Log it loudly so this is visible in the server console
            // too, not just as a silent client-side hang.
            log.error("Live solve failed for session {}", session.getId(), e);
            sendErrorEvent(session, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        } finally {
            session.markFinished();
            SseEmitter emitter = session.getEmitter();
            if (emitter != null) {
                emitter.complete();
            }
        }
    }

    private void onBestSolutionChanged(SolveSession session, BestSolutionChangedEvent<Schedule> event) {
        Schedule schedule = event.getNewBestSolution();
        sendEvent(session, "update", toProgressSnapshot(schedule));
    }

    private void sendEvent(SolveSession session, String eventName, Object data) {
        SseEmitter emitter = session.getEmitter();
        if (emitter == null) return; // browser hasn't connected yet, or already disconnected
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException e) {
            // Browser likely disconnected; nothing more to do for this session.
        }
    }

    private void sendErrorEvent(SolveSession session, String message) {
        SseEmitter emitter = session.getEmitter();
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
        } catch (IOException | IllegalStateException ignored) {
        }
    }

    // ---- Snapshot shapes sent over SSE ----

    // Sent on every "update" event (can fire many times per second during
    // early solving) — deliberately just score + feasibility, no roster
    // data. Rendering a 100+ row table on every improvement is wasted
    // work the user can't actually read anyway; the full roster only
    // matters once, in the final "done" event.
    public record ProgressSnapshot(String score, boolean feasible) {
    }

    public record AssignmentSnapshot(String date, String role, String musicianName) {
    }

    public record ScheduleSnapshot(String score, boolean feasible, List<AssignmentSnapshot> assignments) {
    }

    private ProgressSnapshot toProgressSnapshot(Schedule schedule) {
        HardSoftScore score = schedule.getScore();
        String scoreStr = score != null ? score.toShortString() : "unscored";
        boolean feasible = score != null && score.isFeasible();
        return new ProgressSnapshot(scoreStr, feasible);
    }

    private ScheduleSnapshot toSnapshot(Schedule schedule) {
        HardSoftScore score = schedule.getScore();
        List<AssignmentSnapshot> assignments = schedule.getAssignmentList().stream()
                .sorted(Comparator.comparing(a -> a.getService().getDate()))
                .map(a -> new AssignmentSnapshot(
                        a.getService().getDate().toString(),
                        a.getRole().toString(),
                        a.getMusician() != null ? a.getMusician().getName() : null))
                .collect(Collectors.toList());
        // score can be null very briefly if called before the solver has
        // scored anything yet - guard defensively rather than NPE.
        String scoreStr = score != null ? score.toShortString() : "unscored";
        boolean feasible = score != null && score.isFeasible();
        return new ScheduleSnapshot(scoreStr, feasible, assignments);
    }

    // ---- Duplicated from RosterService on purpose (structure only) ----
    // The Sunday/role/assignment SCAFFOLDING is duplicated here rather
    // than calling a shared private method on RosterService, since that
    // logic is private there. Actual DATA LOADING (musicians, pair
    // preferences) is NOT duplicated - we call rosterService directly
    // for that, so there's exactly one place data comes from the
    // database. If the scaffolding duplication becomes annoying, both
    // could be extracted into a shared ScheduleBuilder class later.

    private Schedule buildUnsolvedSchedule(LocalDate startDate, int numberOfWeeks) {
        List<SundayService> services = new ArrayList<>();
        for (int i = 0; i < numberOfWeeks; i++) {
            services.add(new SundayService(startDate.plusWeeks(i)));
        }

        List<Role> roles = RosterRoles.WEEKLY_ROLES;

        List<Assignment> assignments = new ArrayList<>();
        for (SundayService service : services) {
            for (Role role : roles) {
                assignments.add(new Assignment(service, role));
            }
        }

        List<Musician> musicians = rosterService.loadMusicians();
        List<PairPreference> pairPreferences = rosterService.loadPairPreferences();

        return new Schedule(musicians, services, assignments, pairPreferences);
    }
}