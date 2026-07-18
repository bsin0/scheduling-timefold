package org.churchband.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.churchband.domain.Assignment;
import org.churchband.domain.Musician;
import org.churchband.domain.PairPreference;
import org.churchband.domain.Schedule;
import org.churchband.service.RosterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.timefold.solver.core.api.score.ScoreExplanation;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatch;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;

/**
 * REST entry point for triggering a solve from the (future) HTML frontend.
 *
 * @RestController = @Controller + @ResponseBody: every method's return
 * value is automatically converted to JSON and written to the HTTP
 * response body (Spring does this via Jackson, already on the classpath
 * from spring-boot-starter-web — no extra setup needed).
 *
 * This class deliberately contains almost no logic of its own — it just
 * translates an HTTP request into a call to RosterService.solve(), then
 * translates the resulting Schedule into a plain, JSON-friendly shape
 * (SolveResponse). All the actual solving logic still lives in one
 * place (RosterService), which is exactly why we built it that way.
 */
@RestController
@RequestMapping("/api")
public class RosterController {

    private final RosterService rosterService;

    public RosterController(RosterService rosterService) {
        this.rosterService = rosterService;
    }

    /**
     * POST /api/solve
     * Body: { "startDate": "2026-07-05", "numberOfWeeks": 9, "solveTimeSeconds": 20 }
     * solveTimeSeconds is optional — defaults to 20 if omitted, matching
     * the previous fixed behavior.
     *
     * Runs a full solve and returns the resulting roster as JSON. This
     * call blocks until solving finishes — fine for a manageable solve
     * time; for live progress instead of blocking, see the (future)
     * streaming solve endpoint.
     */
    @PostMapping("/solve")
    public SolveResponse solve(@RequestBody SolveRequest request) {
        int solveTimeSeconds = request.solveTimeSeconds() != null ? request.solveTimeSeconds() : 20;
        Schedule solved = rosterService.solve(request.startDate(), request.numberOfWeeks(), solveTimeSeconds);
        return SolveResponse.from(solved);
    }

    /**
     * GET /api/solve/explain
     *
     * Returns the constraint-by-constraint breakdown for the most recent
     * solve (must call POST /api/solve first in the same server session).
     * Same information App.java used to print to the console — which
     * constraints fired, how many times, and (for violations) which
     * musicians/assignments were involved.
     */
    @GetMapping("/solve/explain")
    public ExplanationResponse explain() {
        ScoreExplanation<Schedule, HardSoftScore> explanation = rosterService.explainLastSolve();

        List<ConstraintView> constraints = new ArrayList<>();
        explanation.getConstraintMatchTotalMap().forEach((constraintId, cmt) ->
                constraints.add(ConstraintView.from(constraintId, cmt)));

        return new ExplanationResponse(
                explanation.getScore().toShortString(),
                explanation.getScore().isFeasible(),
                constraints
        );
    }

    // ---- Request/response shapes ----
    // Plain Java "records" — simple, immutable, JSON-serializable data
    // holders. Jackson (de)serializes these to/from JSON automatically
    // based on field names, no extra annotations needed for this level
    // of simplicity.

    public record SolveRequest(LocalDate startDate, int numberOfWeeks, Integer solveTimeSeconds) {
    }

    public record SolveResponse(String score, boolean feasible, List<AssignmentView> assignments) {
        static SolveResponse from(Schedule schedule) {
            HardSoftScore score = schedule.getScore();

            List<AssignmentView> views = schedule.getAssignmentList().stream()
                    .sorted(Comparator.comparing(a -> a.getService().getDate()))
                    .map(AssignmentView::from)
                    .collect(Collectors.toList());

            return new SolveResponse(score.toShortString(), score.isFeasible(), views);
        }
    }

    public record AssignmentView(LocalDate date, String role, String musicianName) {
        static AssignmentView from(Assignment a) {
            String name = a.getMusician() != null ? a.getMusician().getName() : null;
            return new AssignmentView(a.getService().getDate(), a.getRole().toString(), name);
        }
    }

    public record ExplanationResponse(String score, boolean feasible, List<ConstraintView> constraints) {
    }

    public record ConstraintView(String name, String impact, int matchCount, boolean isViolation,
                                  List<String> matchDescriptions) {
        static ConstraintView from(String constraintId, ConstraintMatchTotal<HardSoftScore> cmt) {
            HardSoftScore impact = cmt.getScore();
            String label = constraintId.contains("/")
                    ? constraintId.substring(constraintId.lastIndexOf('/') + 1)
                    : constraintId;

            boolean isViolation = impact.hardScore() < 0 || impact.softScore() < 0;

            // Only violations get a per-match breakdown (matches App.java's
            // old behavior) — rewards are just shown as a count, since
            // listing every rewarded pairing isn't as useful for debugging
            // as seeing exactly who's causing a penalty.
            List<String> matchDescriptions = isViolation
                    ? cmt.getConstraintMatchSet().stream()
                        .sorted(Comparator.comparing(cm -> cm.getScore().toShortString()))
                        .map(ConstraintView::describeMatch)
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.toList())
                    : List.of();

            return new ConstraintView(label, impact.toShortString(), cmt.getConstraintMatchSet().size(),
                    isViolation, matchDescriptions);
        }

        // Equivalent of App.java's describeMatch — turns a ConstraintMatch's
        // indicted objects (the entities responsible for the score impact)
        // into a readable string.
        private static String describeMatch(ConstraintMatch<HardSoftScore> cm) {
            List<String> parts = new ArrayList<>();
            for (Object obj : cm.getIndictedObjectList()) {
                if (obj instanceof Assignment a) {
                    String musician = a.getMusician() != null ? a.getMusician().getName() : "Unassigned";
                    parts.add(musician + "(" + a.getRole() + " on " + a.getService().getDate() + ")");
                } else if (obj instanceof Musician m) {
                    boolean alreadyCovered = parts.stream().anyMatch(p -> p.startsWith(m.getName()));
                    if (!alreadyCovered) parts.add(m.getName());
                } else if (obj instanceof PairPreference pp) {
                    parts.add(pp.getFirst().getName() + "&" + pp.getSecond().getName());
                }
            }
            return String.join(", ", parts);
        }
    }
}