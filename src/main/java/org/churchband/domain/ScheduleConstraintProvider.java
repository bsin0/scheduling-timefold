package org.churchband.domain;

import java.time.YearMonth;
import java.util.Set;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;

public class ScheduleConstraintProvider implements ConstraintProvider {

    // Scoring for constraints
    //// Soft scores
    private static final int MULTI_ROLE_REWARD = 1;
    private static final int VOCALIST_DOUBLE_UP_REWARD = 4;

    private static final Set<Set<Role>> ALLOWED_MULTI_ROLE_COMBINATIONS = Set.of(
            Set.of(Role.WORSHIP_LEADER, Role.GUITARIST),
            Set.of(Role.BAND_DIRECTOR, Role.GUITARIST),
            Set.of(Role.BAND_DIRECTOR, Role.KEYBOARDIST),
            Set.of(Role.BAND_DIRECTOR, Role.BASSIST),
           // Vocalist doubling — usually guitarists or keyboardists singing backup
            Set.of(Role.GUITARIST, Role.VOCALIST),
            Set.of(Role.GUITARIST, Role.VOCALIST_2),
            Set.of(Role.GUITARIST, Role.VOCALIST_3),
            Set.of(Role.KEYBOARDIST, Role.VOCALIST),
            Set.of(Role.KEYBOARDIST, Role.VOCALIST_2),
            Set.of(Role.KEYBOARDIST, Role.VOCALIST_3)
    );
    // Subset used only to give vocalist-doubling a different reward than the
    // other multi-role combos, since it's more resource-efficient to fill a
    // backup vocalist slot from an existing instrumentalist than to bring in
    // a separate person.
    private static final Set<Role> VOCALIST_ROLES = Set.of(Role.VOCALIST_2, Role.VOCALIST_3);

    private boolean isAllowedMultiRole(Assignment a1, Assignment a2) {
        // Same role cannot be multi-role (should have been caught by avoidSameServiceDoubleBooking)
        if (a1.getRole().equals(a2.getRole())) {
            return false;
        }
        Set<Role> roles = Set.of(a1.getRole(), a2.getRole());
        Musician m = a1.getMusician();
        return ALLOWED_MULTI_ROLE_COMBINATIONS.contains(roles)
                && m != null
                && m.canPerformRole(a1.getRole())
                && m.canPerformRole(a2.getRole());
    }
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                mandatoryRoleMustBeAssigned(factory),
                musicianMustBeAvailable(factory),
                musicianMustBeQualified(factory),
                avoidOverbooking(factory),
                avoidSameServiceDoubleBooking(factory),
                rewardAllowedMultiRole(factory),
                rewardVocalistDoubleUp(factory),
                incrementalDiversityPenalty(factory),
                balanceWorkload(factory),
                bandDirectorCannotBeSolo(factory),
                couplesWithKidsCannotServeSameService(factory),
                couplesPreferTogetherSoft(factory),
                couplesPreferTogetherStrong(factory),
                penalizeConsecutiveServices(factory),
                maxWeeksPerMonthConstraint(factory),
                secondVocalistPreference(factory),
                thirdVocalistPreference(factory)
        };

    }
    // Hard constraints

    // Assignment.musician is allowsUnassigned = true globally (needed so VOCALIST_2 /
    // VOCALIST_3 can be left blank). That means every role, including mandatory ones,
    // is technically optional from the solver's point of view unless we say otherwise.
    // This constraint puts that requirement back for every role except the two
    // optional vocalist slots, using forEachIncludingUnassigned since forEach()
    // silently skips unassigned entities.
    private Constraint mandatoryRoleMustBeAssigned(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(Assignment.class)
                .filter(a -> a.getMusician() == null
                    && a.getRole() != Role.VOCALIST_2
                    && a.getRole() != Role.VOCALIST_3)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Mandatory role left unassigned");
}
    private Constraint musicianMustBeAvailable(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .filter(a -> a.getMusician() != null && !a.getMusician().isAvailableOn(a.getService().getDate()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Unavailable musician assigned");
    }
    private Constraint musicianMustBeQualified(ConstraintFactory factory) {
    return factory.forEach(Assignment.class)
            .filter(a -> a.getMusician() != null
                    && !a.getMusician().canPerformRole(a.getRole()))
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

    private Constraint maxWeeksPerMonthConstraint(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .filter(a -> a.getMusician() != null)
                .groupBy(
                        Assignment::getMusician,
                        a -> YearMonth.from(a.getService().getDate()),
                        ConstraintCollectors.count()
                )
                .filter((musician, yearMonth, count) ->
                        musician.getMaxWeeksPerMonth() != Integer.MAX_VALUE
                                && count > musician.getMaxWeeksPerMonth())
                .penalize(HardSoftScore.ONE_HARD,
                        (musician, yearMonth, count) -> count - musician.getMaxWeeksPerMonth())
                .asConstraint("Musician exceeds max weeks per month");
    }

    // Soft constraints
    private Constraint avoidOverbooking(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .groupBy(Assignment::getMusician, ConstraintCollectors.count())
                .filter((musician, count) -> count > 4)
                .penalize(HardSoftScore.ofSoft(1), (musician, count) -> count - 4)
                .asConstraint("Musician overbooked");
    }

    private Constraint incrementalDiversityPenalty(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .filter(a -> a.getMusician() != null)
                .join(Assignment.class,
                        Joiners.equal(a -> a.getMusician(), Assignment::getMusician))
                .groupBy((a, other) -> a, ConstraintCollectors.countBi())
                .penalize(HardSoftScore.ofSoft(3), (a, countForMusician) -> countForMusician)
                .asConstraint("Incremental diversity: penalize assigning already-overused musician");
    }

    private Constraint balanceWorkload(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .groupBy(Assignment::getMusician, ConstraintCollectors.count())
                .penalize(HardSoftScore.ofSoft(2), (musician, count) -> {
                    int ideal = 4;
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
            .filter((a1, a2) -> !a1.equals(a2)
                    && isAllowedMultiRole(a1, a2)
                    && !VOCALIST_ROLES.contains(a1.getRole())
                    && !VOCALIST_ROLES.contains(a2.getRole()))
            .reward(HardSoftScore.ofSoft(MULTI_ROLE_REWARD))
            .asConstraint("Allowed multi-role usage rewarded");
}

    private Constraint rewardVocalistDoubleUp(ConstraintFactory factory) {
    return factory.forEach(Assignment.class)
            .filter(a -> a.getMusician() != null)
            .join(Assignment.class,
                    Joiners.equal(Assignment::getService),
                    Joiners.equal(Assignment::getMusician))
            .filter((a1, a2) -> !a1.equals(a2)
                    && isAllowedMultiRole(a1, a2)
                    && (VOCALIST_ROLES.contains(a1.getRole()) || VOCALIST_ROLES.contains(a2.getRole())))
            .reward(HardSoftScore.ofSoft(VOCALIST_DOUBLE_UP_REWARD))
            .asConstraint("Reward instrumentalist doubling as backup vocalist");
}

    private Constraint couplesPreferTogetherSoft(ConstraintFactory factory) {
    return factory.forEach(PairPreference.class)
                .filter(pp -> pp.getType() == PairPreferenceType.PREFER_TOGETHER_SAME_SERVICE_SOFT)
                .join(Assignment.class, Joiners.equal(pp -> pp.getFirst(), Assignment::getMusician))
                .ifNotExists(Assignment.class,
                        Joiners.equal((pp, a1) -> a1.getService(), Assignment::getService),
                        Joiners.equal((pp, a1) -> pp.getSecond(), Assignment::getMusician))
                .penalize(HardSoftScore.ofSoft(2))
                .asConstraint("Couples prefer serving together (optional)");
    }

    private Constraint couplesPreferTogetherStrong(ConstraintFactory factory) {
    return factory.forEach(PairPreference.class)
                .filter(pp -> pp.getType() == PairPreferenceType.PREFER_TOGETHER_SAME_SERVICE_STRONG)
                .join(Assignment.class, Joiners.equal(pp -> pp.getFirst(), Assignment::getMusician))
                .ifNotExists(Assignment.class,
                        Joiners.equal((pp, a1) -> a1.getService(), Assignment::getService),
                        Joiners.equal((pp, a1) -> pp.getSecond(), Assignment::getMusician))
                .penalize(HardSoftScore.ofSoft(10))
                .asConstraint("Couples prefer serving together (one car - strong)");
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
                .penalize(HardSoftScore.ofSoft(3))
                .asConstraint("Penalize consecutive weekly assignments");
    }

    // Reward vocalist assignments (more favorable than other constraints)
    private Constraint secondVocalistPreference(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .filter(a -> a.getRole() == Role.VOCALIST_2 && a.getMusician() != null)
                // Only reward if musician is explicitly qualified for VOCALIST_2
                .filter(a -> a.getMusician().canPerformRole(Role.VOCALIST_2))
                .reward(HardSoftScore.ofSoft(4))
                .asConstraint("Reward having vocalist_2");
    }

    // Reward additional vocalists (VOCALIST_3) - soft, optional
    private Constraint thirdVocalistPreference(ConstraintFactory factory) {
        return factory.forEach(Assignment.class)
                .filter(a -> a.getRole() == Role.VOCALIST_3 && a.getMusician() != null)
                // Only reward if musician is explicitly qualified for VOCALIST_3
                .filter(a -> a.getMusician().canPerformRole(Role.VOCALIST_3))
                .reward(HardSoftScore.ofSoft(1))
                .asConstraint("Reward having vocalist_3");
    }

}
