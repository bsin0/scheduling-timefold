
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

        // 1) Sundays over 8 weeks from 2026-01-04
        List<SundayService> services = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 1, 4);
        for (int i = 0; i < 8; i++) {
            services.add(new SundayService(start.plusWeeks(i)));
        }
        // Dates: 2026-01-04, 01-11, 01-18, 01-25, 02-01, 02-08, 02-15, 02-22
        // EXCEPTION: Skip 2026-01-11 (special case)
        services.removeIf(s -> s.getDate().equals(LocalDate.of(2026, 1, 11)));

        // Now 'services' contains 7 Sundays, excluding Jan 11

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
                .withTerminationSpentLimit(Duration.ofSeconds(20))
                .withEnvironmentMode(EnvironmentMode.FULL_ASSERT);

        SolverFactory<Schedule> solverFactory = SolverFactory.create(solverConfig);
        Solver<Schedule> solver = solverFactory.buildSolver();

        Schedule solvedSchedule = solver.solve(unsolvedSchedule);

        // 7) Full schedule as CSV
        System.out.println("date,role,musician");

        solvedSchedule.getAssignmentList().stream()
                .sorted(Comparator.comparing(a -> a.getService().getDate()))
                .forEach(a -> {
                    LocalDate date = a.getService().getDate();
                    String role = a.getRole().toString();
                    String musician = a.getMusician() != null ? a.getMusician().getName() : "Unassigned";

                    System.out.printf("%s,%s,%s%n", date, role, musician);
                });

        // Blank line to separate sections
        System.out.println();
        System.out.println();

        // 8) Summary section as CSV
        System.out.println("musician,date,roles");

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

                    dateRolesMap.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey()) // chronological by date
                            .forEach(e -> {
                                LocalDate date = e.getKey();

                                // FIX: rename this variable
                                String rolesJoined = e.getValue().stream()
                                        .map(Role::toString)
                                        .collect(Collectors.joining("|"));

                                System.out.printf("%s,%s,%s%n", musician, date, rolesJoined);
                            });
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
