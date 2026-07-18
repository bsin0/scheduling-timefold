package org.churchband.domain;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static holder for constraint weight values, read by
 * ScheduleConstraintProvider at constraint-definition time.
 *
 * WHY THIS EXISTS (the awkward part worth understanding):
 * Timefold constructs ScheduleConstraintProvider itself, via a plain
 * no-arg constructor — it has no idea Spring exists, so Spring can't
 * inject WeightService into it the normal way. Rather than fight that,
 * we use one small static, thread-safe map as a bridge: RosterService
 * (which DOES have access to WeightService) copies the current weight
 * values in here immediately before every solve, and
 * ScheduleConstraintProvider reads from here instead of from Spring.
 *
 * This means weights are effectively "frozen" for the duration of a
 * single solve — if someone changes a weight via the API mid-solve, it
 * won't affect that in-progress solve, only the next one. That's
 * actually the correct behavior: you want a solve's constraint weights
 * to stay consistent throughout, not shift underneath it.
 */
public final class ConstraintWeights {

    private static final Map<String, Integer> CURRENT = new ConcurrentHashMap<>();

    private ConstraintWeights() {
    }

    /** Called by RosterService right before each solve to load current values. */
    public static void set(Map<String, Integer> weights) {
        CURRENT.clear();
        CURRENT.putAll(weights);
    }

    /**
     * Called by ScheduleConstraintProvider's constraint methods. Throws
     * if a weight is missing rather than silently defaulting to 0 — a
     * missing weight almost certainly means set() wasn't called before
     * solving, which is a real bug worth surfacing loudly rather than
     * quietly producing a nonsensical schedule.
     */
    public static int get(String name) {
        Integer value = CURRENT.get(name);
        if (value == null) {
            throw new IllegalStateException(
                    "Constraint weight \"" + name + "\" was not set before solving. "
                            + "This means ConstraintWeights.set(...) wasn't called — check RosterService.solve().");
        }
        return value;
    }
}