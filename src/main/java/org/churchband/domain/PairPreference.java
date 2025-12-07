
package org.churchband.domain;

import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty;


public final class PairPreference {

    private final Musician first;
    private final Musician second;
    private final PairPreferenceType type;

    public PairPreference(Musician first, Musician second, PairPreferenceType type) {
        this.first = first;
        this.second = second;
        this.type = type;
    }

    @ProblemFactProperty
    public Musician getFirst() { return first; }

    @ProblemFactProperty
    public Musician getSecond() { return second; }

    @ProblemFactProperty
    public PairPreferenceType getType() { return type; }
}
