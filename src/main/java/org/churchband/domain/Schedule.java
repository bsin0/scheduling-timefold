
package org.churchband.domain;

import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;

import java.util.List;

@PlanningSolution
public class Schedule {

    @ValueRangeProvider(id = "musicianRange")
    private List<Musician> musicianList;

    private List<SundayService> serviceList;

    @PlanningEntityCollectionProperty
    private List<Assignment> assignmentList;

    // Pair preferences as problem facts (immutable to the solver)
    @ProblemFactCollectionProperty
    private List<PairPreference> pairPreferenceList;

    @PlanningScore
    private HardSoftScore score;

    public Schedule() {
        // Required by Timefold
    }

    public Schedule(List<Musician> musicianList,
                    List<SundayService> serviceList,
                    List<Assignment> assignmentList) {
        this.musicianList = musicianList;
        this.serviceList = serviceList;
        this.assignmentList = assignmentList;
    }

    // Convenience constructor including pair preferences
    public Schedule(List<Musician> musicianList,
                    List<SundayService> serviceList,
                    List<Assignment> assignmentList,
                    List<PairPreference> pairPreferenceList) {
        this.musicianList = musicianList;
        this.serviceList = serviceList;
        this.assignmentList = assignmentList;
        this.pairPreferenceList = pairPreferenceList;
    }

    public List<Musician> getMusicianList() {
        return musicianList;
    }

    public void setMusicianList(List<Musician> musicianList) {
        this.musicianList = musicianList;
    }

    public List<SundayService> getServiceList() {
        return serviceList;
    }

    public void setServiceList(List<SundayService> serviceList) {
        this.serviceList = serviceList;
    }

    public List<Assignment> getAssignmentList() {
        return assignmentList;
    }

    public void setAssignmentList(List<Assignment> assignmentList) {
        this.assignmentList = assignmentList;
    }

    public List<PairPreference> getPairPreferenceList() {
        return pairPreferenceList;
    }

    public void setPairPreferenceList(List<PairPreference> pairPreferenceList) {
        this.pairPreferenceList = pairPreferenceList;
    }

    public HardSoftScore getScore() {
        return score;
    }

    public void setScore(HardSoftScore score) {
        this.score = score;
    }
}
