package org.churchband.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.churchband.domain.Assignment;
import org.churchband.domain.ConstraintWeights;
import org.churchband.domain.Musician;
import org.churchband.domain.PairPreference;
import org.churchband.domain.Role;
import org.churchband.domain.Schedule;
import org.churchband.domain.ScheduleConstraintProvider;
import org.churchband.domain.SundayService;
import org.churchband.persistence.BlockoutEntity;
import org.churchband.persistence.BlockoutRepository;
import org.churchband.persistence.MusicianEntity;
import org.churchband.persistence.MusicianRepository;
import org.churchband.persistence.PairPreferenceEntity;
import org.churchband.persistence.PairPreferenceRepository;
import org.churchband.persistence.WeightEntity;
import org.springframework.stereotype.Service;

import ai.timefold.solver.core.api.score.ScoreExplanation;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;

/**
 * Loads roster data from the database and converts it into the plain
 * domain objects (Musician, PairPreference) that the Timefold solver
 * already knows how to work with.
 *
 * This class is the database equivalent of what RosterCsv used to do —
 * RosterCsv read CSV files and built List<Musician>/List<PairPreference>;
 * this reads the database and builds the exact same shapes. Nothing about
 * Musician, PairPreference, Schedule, or ScheduleConstraintProvider needs
 * to change, because they never knew about CSV OR the database — they
 * only ever dealt with plain domain objects.
 *
 * @Service marks this as a Spring-managed component, so it (and its
 * repository dependencies) get created and wired automatically — nothing
 * calls `new RosterService(...)` by hand anywhere.
 */
@Service
public class RosterService {

    private final MusicianRepository musicianRepository;
    private final BlockoutRepository blockoutRepository;
    private final PairPreferenceRepository pairPreferenceRepository;
    private final WeightService weightService;

    // Caches the most recent solve result so a later call to explain()
    // (from the separate /api/solve/explain endpoint) doesn't need the
    // caller to somehow pass the whole solved Schedule back over HTTP.
    // Simple for a single-admin tool like this; if this app ever supports
    // multiple concurrent users solving different rosters at once, this
    // would need to become a per-session or per-request cache instead.
    private Schedule lastSolvedSchedule;

    /**
     * Lets LiveSolveService register its final solved Schedule here once
     * a streaming solve finishes, so /api/solve/explain works after a
     * live solve too — not just after the older blocking solve() call.
     * Both solve paths end up writing to the same field, so
     * explainLastSolve() always reflects whichever solve (blocking or
     * live) finished most recently.
     */
    public void registerLastSolvedSchedule(Schedule schedule) {
        this.lastSolvedSchedule = schedule;
    }

    public RosterService(MusicianRepository musicianRepository,
                          BlockoutRepository blockoutRepository,
                          PairPreferenceRepository pairPreferenceRepository,
                          WeightService weightService) {
        this.musicianRepository = musicianRepository;
        this.blockoutRepository = blockoutRepository;
        this.pairPreferenceRepository = pairPreferenceRepository;
        this.weightService = weightService;
    }

    /**
     * Loads every musician from the database, attaching their blocked
     * dates. Equivalent to RosterCsv.loadMusiciansCsv(path).
     */
    public List<Musician> loadMusicians() {
        List<MusicianEntity> entities = musicianRepository.findAll();

        return entities.stream()
                .map(this::toDomainMusician)
                .collect(Collectors.toList());
    }

    private Musician toDomainMusician(MusicianEntity entity) {
        List<BlockoutEntity> blockouts = blockoutRepository.findByMusicianId(entity.getId());

        Set<LocalDate> blockedDates = blockouts.stream()
                .map(BlockoutEntity::getBlockedDate)
                .collect(Collectors.toSet());

        // MusicianEntity stores "no limit" as null; Musician (the domain
        // object) uses Integer.MAX_VALUE for the same concept, matching
        // what RosterCsv did when a CSV cell was blank.
        int maxWeeksPerMonth = entity.getMaxWeeksPerMonth() == null
                ? Integer.MAX_VALUE
                : entity.getMaxWeeksPerMonth();

        return new Musician(
                entity.getId(),
                entity.getName(),
                entity.getRoles(),
                blockedDates,
                maxWeeksPerMonth
        );
    }

    /**
     * Loads every pair preference from the database. Equivalent to
     * RosterCsv.loadPairPreferencesCsv(path, byId).
     */
    public List<PairPreference> loadPairPreferences() {
        List<Musician> musicians = loadMusicians();
        Map<String, Musician> byId = new LinkedHashMap<>();
        for (Musician m : musicians) {
            byId.put(m.getId(), m);
        }

        List<PairPreferenceEntity> entities = pairPreferenceRepository.findAll();

        return entities.stream()
                .map(e -> {
                    Musician first = byId.get(e.getFirstMusicianId());
                    Musician second = byId.get(e.getSecondMusicianId());
                    if (first == null || second == null) {
                        throw new IllegalStateException(
                                "Pair preference references unknown musician id: "
                                        + e.getFirstMusicianId() + " / " + e.getSecondMusicianId());
                    }
                    return new PairPreference(first, second, e.getType());
                })
                .collect(Collectors.toList());
    }

    /**
     * Builds the full unsolved Schedule (services + role assignments for
     * each Sunday) and runs the Timefold solver against it, using
     * musicians and pair preferences loaded from the database.
     *
     * @param startDate       the first Sunday of the roster window
     * @param numberOfWeeks   how many consecutive Sundays to roster
     * @param solveTimeSeconds how long to let the solver search before
     *                         stopping and returning the best schedule
     *                         found so far
     */
    public Schedule solve(LocalDate startDate, int numberOfWeeks, int solveTimeSeconds) {
        // Load current weights from the database and hand them to
        // ScheduleConstraintProvider via the static ConstraintWeights
        // bridge, before Timefold starts evaluating any constraints.
        // See ConstraintWeights.java for why this indirection exists.
        Map<String, Integer> weights = weightService.listAll().stream()
                .collect(Collectors.toMap(WeightEntity::getName, WeightEntity::getValue));
        ConstraintWeights.set(weights);

        Schedule unsolvedSchedule = buildUnsolvedSchedule(startDate, numberOfWeeks);

        SolverFactory<Schedule> solverFactory = buildSolverFactory(solveTimeSeconds);
        Solver<Schedule> solver = solverFactory.buildSolver();

        Schedule solved = solver.solve(unsolvedSchedule);
        this.lastSolvedSchedule = solved;
        return solved;
    }

    /**
     * Explains the most recently solved Schedule (from the last call to
     * solve()). Throws if solve() hasn't been called yet this session.
     *
     * Safe with respect to ConstraintWeights: solve() populates it right
     * before solving and nothing clears it afterward, so the same
     * weights that produced lastSolvedSchedule are still in place when
     * this re-evaluates constraints for the explanation.
     */
    public ScoreExplanation<Schedule, HardSoftScore> explainLastSolve() {
        if (lastSolvedSchedule == null) {
            throw new IllegalStateException("No solve has been run yet — call /api/solve first.");
        }
        return explain(lastSolvedSchedule);
    }

    /**
     * Returns a full constraint-by-constraint breakdown of a solved
     * Schedule's score — which constraints fired, how many times, and
     * (for violations) which musicians/assignments were involved.
     *
     * This is the same information App.java used to print to the
     * console after solving. Kept as a separate call from solve() (per
     * the /api/solve/explain design) since building the explanation is
     * extra work you don't need on every normal solve — only when
     * actively debugging or tuning constraint weights.
     *
     * NOTE: must be called with a Schedule that came from solve() (i.e.
     * already has a score) — explaining an unsolved Schedule doesn't
     * make sense and will error.
     */
    public ScoreExplanation<Schedule, HardSoftScore> explain(Schedule solvedSchedule) {
        // Termination time is irrelevant here — explain() only re-evaluates
        // constraints against an already-solved Schedule, it doesn't run
        // the search loop. The value passed to buildSolverFactory() is
        // unused in practice, but a SolverConfig requires one.
        SolverFactory<Schedule> solverFactory = buildSolverFactory(20);
        SolutionManager<Schedule, HardSoftScore> solutionManager = SolutionManager.create(solverFactory);
        return solutionManager.explain(solvedSchedule);
    }

    private Schedule buildUnsolvedSchedule(LocalDate startDate, int numberOfWeeks) {
        List<SundayService> services = new ArrayList<>();
        for (int i = 0; i < numberOfWeeks; i++) {
            services.add(new SundayService(startDate.plusWeeks(i)));
        }

        List<Role> roles = List.of(
                Role.WORSHIP_LEADER,
                Role.VOCALIST,
                Role.VOCALIST_2,
                Role.VOCALIST_3,
                Role.BASSIST,
                Role.DRUMMER,
                Role.KEYBOARDIST,
                Role.GUITARIST,
                Role.BAND_DIRECTOR,
                Role.SOUND,
                Role.LYRICS,
                Role.CAMERA
        );

        List<Assignment> assignments = new ArrayList<>();
        for (SundayService service : services) {
            for (Role role : roles) {
                assignments.add(new Assignment(service, role));
            }
        }

        List<Musician> musicians = loadMusicians();
        List<PairPreference> pairPreferences = loadPairPreferences();

        return new Schedule(musicians, services, assignments, pairPreferences);
    }

    private SolverFactory<Schedule> buildSolverFactory(int solveTimeSeconds) {
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(Schedule.class)
                .withEntityClasses(Assignment.class)
                .withConstraintProviderClass(ScheduleConstraintProvider.class)
                .withTerminationSpentLimit(Duration.ofSeconds(solveTimeSeconds))
                .withEnvironmentMode(EnvironmentMode.FULL_ASSERT);

        return SolverFactory.create(solverConfig);
    }
}