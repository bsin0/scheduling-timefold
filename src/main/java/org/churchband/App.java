
package org.churchband;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.EnvironmentMode;

import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.score.ScoreExplanation;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;

import org.churchband.domain.*;
import org.churchband.util.RosterCsv;

import java.time.LocalDate;
import java.time.Duration;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class App {

    public static void main(String[] args) {

        // 1) Sundays over 8 weeks from 2025-11-09
        List<SundayService> services = new ArrayList<>();
        LocalDate start = LocalDate.of(2025, 11, 9);
        for (int i = 0; i < 8; i++) {
            services.add(new SundayService(start.plusWeeks(i)));
        }
        // Dates: 2025-11-09, 11-16, 11-23, 11-30, 12-07, 12-14, 12-21, 12-28

        // 2) Roles per Sunday
        List<Role> roles = List.of(
                Role.WORSHIP_LEADER,
                Role.VOCALIST,
                Role.BASSIST,
                Role.DRUMMER,
                Role.KEYBOARDIST,
                Role.GUITARIST,
                Role.BAND_DIRECTOR,
                Role.SOUND,
                Role.LYRICS,
                Role.CAMERA
        );

        // 3) Load musicians and pair preferences from CSV
        Path musiciansCsv = Path.of("config/musicians.csv");
        Path pairsCsv     = Path.of("config/pairs.csv");

        // Validate musicians CSV (friendly report)
        List<String> musIssues = RosterCsv.validateMusiciansCsv(musiciansCsv);
        if (!musIssues.isEmpty()) {
            System.err.println("Musicians CSV validation issues:");
            musIssues.forEach(s -> System.err.println(" - " + s));
            // Optional: abort if issues found
            // System.exit(1);
        }

        // Load musicians
        List<Musician> musicians;
        try {
            musicians = RosterCsv.loadMusiciansCsv(musiciansCsv);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load musicians CSV: " + e.getMessage(), e);
        }

        // id → musician index
        Map<String, Musician> byId = musicians.stream()
                .collect(Collectors.toMap(Musician::getId, m -> m, (a, b) -> b, LinkedHashMap::new));

        // Validate pairs CSV (friendly report)
        List<String> pairIssues = RosterCsv.validatePairsCsv(pairsCsv, byId.keySet());
        if (!pairIssues.isEmpty()) {
            System.err.println("Pairs CSV validation issues:");
            pairIssues.forEach(s -> System.err.println(" - " + s));
            // Optional: abort if issues found
            // System.exit(1);
        }

        // Load pair preferences
        List<PairPreference> pairPreferences;
        try {
            pairPreferences = RosterCsv.loadPairPreferencesCsv(pairsCsv, byId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load pairs CSV: " + e.getMessage(), e);
        }

        // 4) Create assignments (each Sunday requires every listed role)
        List<Assignment> assignments = new ArrayList<>();
        for (SundayService service : services) {
            for (Role role : roles) {
                assignments.add(new Assignment(service, role));
            }
        }

        // 5) Build schedule
        Schedule unsolvedSchedule = new Schedule(musicians, services, assignments, pairPreferences);

        // 6) Configure and solve
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(Schedule.class)
                .withEntityClasses(Assignment.class)
                .withConstraintProviderClass(ScheduleConstraintProvider.class)
                .withTerminationSpentLimit(Duration.ofSeconds(10))
                .withEnvironmentMode(EnvironmentMode.FULL_ASSERT);

        SolverFactory<Schedule> solverFactory = SolverFactory.create(solverConfig);
        Solver<Schedule> solver = solverFactory.buildSolver();

        Schedule solvedSchedule = solver.solve(unsolvedSchedule);

        // 7) Print solved schedule (sorted by date)
        System.out.printf("%-12s %-15s %-20s%n", "Date", "Role", "Musician");

        System.out.println("------------------------------------------------------------");
        solvedSchedule.getAssignmentList().stream()
                .sorted(Comparator.comparing(a -> a.getService().getDate()))
                .forEach(a -> System.out.printf("%-12s %-15s %-20s%n",
                        a.getService().getDate(),
                        a.getRole(),
                        a.getMusician() != null ? a.getMusician().getName() : "Unassigned"));



        // 8) Alphabetical summary by musician, chronological by date
        System.out.println("\nSummary: Roles served by each musician per date");
        System.out.println("------------------------------------------------------------");
        Map<String, Map<LocalDate, List<Role>>> musicianRolesByDate =
                solvedSchedule.getAssignmentList().stream()
                        .filter(a -> a.getMusician() != null)
                        .collect(Collectors.groupingBy(
                                a -> a.getMusician().getName(),
                                Collectors.groupingBy(
                                        a -> a.getService().getDate(),
                                        Collectors.mapping(Assignment::getRole, Collectors.toList())
                                )
                        ));

        musicianRolesByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // alphabetical by musician
                .forEach(entry -> {
                    String musician = entry.getKey();
                    Map<LocalDate, List<Role>> dateRolesMap = entry.getValue();
                    System.out.printf("%-15s : %d Sundays%n", musician, dateRolesMap.size());
                    dateRolesMap.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey()) // chronological by date
                            .forEach(e -> System.out.printf("  %s -> %s%n", e.getKey(), e.getValue()));
                    System.out.println();
                });



        // 9) Score explanation (portable across Timefold versions)
        SolutionManager<Schedule, HardSoftScore> solutionManager = SolutionManager.create(solverFactory);
        ScoreExplanation<Schedule, HardSoftScore> explanation = solutionManager.explain(solvedSchedule);


        // Build an index from each ConstraintMatch to its (non-deprecated) constraint ID.
        Map<ai.timefold.solver.core.api.score.constraint.ConstraintMatch<HardSoftScore>, String> matchToId = new HashMap<>();
        explanation.getConstraintMatchTotalMap().forEach((constraintId, cmt) -> {
            cmt.getConstraintMatchSet().forEach(cm -> matchToId.put(cm, constraintId));
        });


// --- Print ALL matches by constraint (hard + soft), with indicted objects:
        System.out.println("\n=== All Constraint Matches ===");
        explanation.getConstraintMatchTotalMap().forEach((constraintId, cmt) -> {
            System.out.printf("%s -> total impact: %s%n", constraintId, cmt.getScore().toShortString());
            cmt.getConstraintMatchSet().forEach(cm -> {
                System.out.printf("  match: %s | objects: %s%n",
                        cm.getScore().toShortString(), cm.getIndictedObjectList());
            });
        });

// --- Print HARD violations only:
        System.out.println("\n=== HARD Constraint Matches Only ===");
        explanation.getConstraintMatchTotalMap().forEach((constraintId, cmt) -> {
            boolean hasNegativeHardImpact = !cmt.getScore().isFeasible();
            if (hasNegativeHardImpact) {
                System.out.printf("%s -> total impact: %s%n", constraintId, cmt.getScore().toShortString());
                cmt.getConstraintMatchSet().forEach(cm -> {
                    if (!cm.getScore().isFeasible()) {
                        System.out.printf("  match: %s | objects: %s%n",
                                cm.getScore().toShortString(), cm.getIndictedObjectList());
                    }
                });
            }
        });

// --- Per assignment (who broke what), using the constraint ID index (no deprecated methods):
        System.out.println("\n=== HARD Violations Per Assignment ===");
        explanation.getIndictmentMap().forEach((obj, indictment) -> {
            if (obj instanceof Assignment a) {
                var hardMatches = indictment.getConstraintMatchSet().stream()
                        .filter(cm -> !cm.getScore().isFeasible())
                        .toList();
                if (!hardMatches.isEmpty()) {
                    System.out.printf("Assignment: %s on %s for role %s%n",
                            a.getMusician() != null ? a.getMusician().getName() : "Unassigned",
                            a.getService().getDate(),
                            a.getRole());
                    hardMatches.forEach(cm -> {
                        String id = matchToId.get(cm); // e.g., "org.churchband/Unavailable musician assigned"
                        System.out.printf("  broke: %s | impact: %s%n", id, cm.getScore().toShortString());
                    });
                    System.out.println("----");
                }
            }
        });



    }
}
