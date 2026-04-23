
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

    private static String describeMatch(List<?> indictedObjects) {
        List<String> parts = new ArrayList<>();
        for (Object obj : indictedObjects) {
            if (obj instanceof Assignment a) {
                String musician = a.getMusician() != null ? a.getMusician().getName() : "Unassigned";
                parts.add(musician + " (" + a.getRole() + " on " + a.getService().getDate() + ")");
            } else if (obj instanceof Musician m) {
                // Avoid duplicating if already captured via Assignment above
                boolean alreadyCovered = parts.stream().anyMatch(p -> p.startsWith(m.getName()));
                if (!alreadyCovered) parts.add(m.getName());
            } else if (obj instanceof PairPreference pp) {
                parts.add(pp.getFirst().getName() + " & " + pp.getSecond().getName());
            }
        }
        return String.join(", ", parts);
    }

    public static void main(String[] args) {

        // 1) Sundays over 9 weeks from 2026-03-01
        List<SundayService> services = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 5, 3);
        for (int i = 0; i < 9; i++) {
            services.add(new SundayService(start.plusWeeks(i)));
        }
        // E.g. Dates: 2026-01-04, 01-11, 01-18, 01-25, 02-01, 02-08, 02-15, 02-22
        // E.g. EXCEPTION: Skip 2026-01-11 (special case) uncomment next line if need to exclude a specific date
        //// services.removeIf(s -> s.getDate().equals(LocalDate.of(2026, 1, 11)));
        //// Now 'services' contains 7 Sundays, excluding Jan 11

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

        // 9) Score summary
        SolutionManager<Schedule, HardSoftScore> solutionManager = SolutionManager.create(solverFactory);
        ScoreExplanation<Schedule, HardSoftScore> explanation = solutionManager.explain(solvedSchedule);
        HardSoftScore score = solvedSchedule.getScore();

        System.out.println("\n============================================================");
        System.out.printf("  SCORE: %s  |  %s%n",
                score.toShortString(),
                score.isFeasible() ? "✓ FEASIBLE (no hard violations)" : "✗ INFEASIBLE (hard violations exist)");
        System.out.println("============================================================");

        List<String> hardLines = new ArrayList<>();
        List<String> softLines = new ArrayList<>();

        explanation.getConstraintMatchTotalMap().forEach((constraintId, cmt) -> {
            HardSoftScore impact = cmt.getScore();
            int matchCount = cmt.getConstraintMatchSet().size();
            String label = constraintId.contains("/")
                    ? constraintId.substring(constraintId.lastIndexOf('/') + 1)
                    : constraintId;

            boolean isHardViolation = impact.hardScore() < 0;
            boolean isSoftPenalty   = impact.softScore() < 0;
            boolean isSoftReward    = impact.softScore() > 0;

            if (!isHardViolation && !isSoftPenalty && !isSoftReward) return;

            String header = String.format("  %s %-50s %6s  (%d match%s)",
                    isHardViolation || isSoftPenalty ? "✗" : "✓",
                    label, impact.toShortString(), matchCount, matchCount == 1 ? "" : "es");

            List<String> matchLines = new ArrayList<>();
            if (isHardViolation || isSoftPenalty) {
                cmt.getConstraintMatchSet().stream()
                        .sorted(Comparator.comparing(cm -> cm.getScore().toShortString()))
                        .forEach(cm -> {
                            String detail = describeMatch(cm.getIndictedObjectList());
                            if (!detail.isBlank()) {
                                matchLines.add("      → " + detail);
                            }
                        });
            }

            List<String> target = isHardViolation ? hardLines : softLines;
            target.add(header);
            target.addAll(matchLines);
        });

        System.out.println("\nHARD CONSTRAINTS:");
        if (hardLines.isEmpty()) {
            System.out.println("  ✓ All satisfied");
        } else {
            hardLines.forEach(System.out::println);
        }

        System.out.println("\nSOFT CONSTRAINTS:");
        if (softLines.isEmpty()) {
            System.out.println("  (none triggered)");
        } else {
            softLines.forEach(System.out::println);
        }
        System.out.println("============================================================\n");
    }
}
