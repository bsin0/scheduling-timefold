package org.churchband.domain;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;

import java.util.Set;

public class ScheduleConstraintProvider implements ConstraintProvider {

    private static final Set<Set<Role>> ALLOWED_MULTI_ROLE_COMBINATIONS = Set.of(
            Set.of(Role.WORSHIP_LEADER, Role.GUITARIST),
            Set.of(Role.BAND_DIRECTOR, Role.GUITARIST),
            Set.of(Role.BAND_DIRECTOR, Role.KEYBOARDIST),
            Set.of(Role.BAND_DIRECTOR, Role.BASSIST)
    );
    private boolean isAllowedMultiRole(Assignment a1, Assignment a2) {
        Set<Role> roles = Set.of(a1.getRole(), a2.getRole());
        return ALLOWED_MULTI_ROLE_COMBINATIONS.contains(roles)
                && a1.getMusician().canPerformRole(a1.getRole())
                && a1.getMusician().canPerformRole(a2.getRole());
    }
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                musicianMustBeAvailable(factory),
                musicianMustBeQualified(factory),
                avoidOverbooking(factory),
                avoidSameServiceDoubleBooking(factory),
                rewardAllowedMultiRole(factory),
                incrementalDiversityPenalty(factory),
                balanceWorkload(factory),
                bandDirectorCannotBeSolo(factory),
                couplesWithKidsCannotServeSameService(factory),
                couplesPreferTogetherPenaltyWhenAlone(factory),
                penalizeConsecutiveServices(factory),
                musicianMustBeCapableOfRole(factory)
        };

    }
    // Hard constraints
    private Constraint musicianMustBeAvailable(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .filter(a -> a.getMusician() != null && !a.getMusician().isAvailableOn(a.getService().getDate()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Unavailable musician assigned");
    }
    private Constraint musicianMustBeQualified(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .filter(a -> a.getMusician() != null && !a.getMusician().canPerformRole(a.getRole()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Unqualified musician assigned");
    }
    private Constraint bandDirectorCannotBeSolo(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .filter(a -> a.getMusician() != null)
                .groupBy(Assignment::getService, Assignment::getMusician,
                        ConstraintCollectors.toSet(Assignment::getRole))
                .filter((service, musician, roles) -> roles.contains(Role.BAND_DIRECTOR) && roles.size() == 1)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Band Director cannot be solo");
    }
    private Constraint avoidSameServiceDoubleBooking(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .filter(a -> a.getMusician() != null)
                .join(Assignment.class,
                        Joiners.equal(Assignment::getService),
                        Joiners.equal(Assignment::getMusician))
                .filter((a1, a2) -> !a1.equals(a2) && !isAllowedMultiRole(a1, a2))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Musician double-booked in same service");
    }

    private Constraint couplesWithKidsCannotServeSameService(ConstraintFactory factory) {
        return factory.forEach(PairPreference.class)
                .filter(pp -> pp.getType() == PairPreferenceType.NOT_TOGETHER_SAME_SERVICE_HARD)
                .join(Assignment.class,
                        Joiners.equal(pp -> pp.getFirst(), Assignment::getMusician))
                .join(Assignment.class,
                        Joiners.equal((pp, a1) -> a1.getService(), Assignment::getService),
                        Joiners.equal((pp, a1) -> pp.getSecond(), Assignment::getMusician))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Couples (kids): partners cannot both serve same service");
    }

    private Constraint musicianMustBeCapableOfRole(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .filter(a ->
                        a.getMusician() != null &&
                                !a.getMusician().canPerformRole(a.getRole())
                )
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Musician must be capable of assigned role");
    }

    // Soft constraints
    private Constraint avoidOverbooking(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .groupBy(Assignment::getMusician, ConstraintCollectors.count())
                .filter((musician, count) -> count > 4) // Change "4" if max assignments per musician changes with horizon
                .penalize(HardSoftScore.ofSoft(1), (musician, count) -> count - 4) // Also update "4" here for penalty calculation
                .asConstraint("Musician overbooked");
    }

    private Constraint incrementalDiversityPenalty(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .filter(a -> a.getMusician() != null)
                // Join the current musician's total count (dynamic during solving)
                .join(Assignment.class,
                        Joiners.equal(a -> a.getMusician(), Assignment::getMusician))
                .groupBy((a, other) -> a, ConstraintCollectors.countBi()) // count of all assignments for that musician
                // Penalize the *current* assignment by how many times the musician is already used.
                .penalize(HardSoftScore.ofSoft(5), (a, countForMusician) -> countForMusician)
                .asConstraint("Incremental diversity: penalize assigning already-overused musician");
    }

    private Constraint balanceWorkload(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .groupBy(Assignment::getMusician, ConstraintCollectors.count())
                .penalize(HardSoftScore.ofSoft(1), (musician, count) -> {
                    int ideal = 4; // Adjust based on your planning horizon, based off overall count at end of solver
                    return Math.abs(count - ideal);
                })
                .asConstraint("Balance workload across musicians");
    }
    private Constraint rewardAllowedMultiRole(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .filter(a -> a.getMusician() != null)
                .join(Assignment.class,
                        Joiners.equal(Assignment::getService),
                        Joiners.equal(Assignment::getMusician))
                .filter((a1, a2) -> !a1.equals(a2) && isAllowedMultiRole(a1, a2))
                .reward(HardSoftScore.ONE_SOFT)
                .asConstraint("Allowed multi-role usage rewarded");
    }

    private Constraint couplesPreferTogetherPenaltyWhenAlone(ConstraintFactory factory) {
        return factory.forEach(PairPreference.class)
                .filter(pp -> pp.getType() == PairPreferenceType.PREFER_TOGETHER_SAME_SERVICE_SOFT)
                .join(Assignment.class, Joiners.equal(pp -> pp.getFirst(), Assignment::getMusician))
                .ifNotExists(Assignment.class,
                        Joiners.equal((pp, a1) -> a1.getService(), Assignment::getService),
                        Joiners.equal((pp, a1) -> pp.getSecond(), Assignment::getMusician))
                .penalize(HardSoftScore.ONE_SOFT)
                .asConstraint("Couples prefer serving together (penalize when alone)");
    }

    private Constraint penalizeConsecutiveServices(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .filter(a -> a.getMusician() != null)
                .join(Assignment.class,
                        Joiners.equal(Assignment::getMusician),
                        Joiners.filtering((a1, a2) ->
                                a1.getService().getDate().plusWeeks(1)
                                        .equals(a2.getService().getDate())
                        )
                )
                .penalize(HardSoftScore.ofSoft(10)) // adjust strength
                .asConstraint("Penalize consecutive weekly assignments");
    }

}
