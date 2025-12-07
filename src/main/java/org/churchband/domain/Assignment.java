package org.churchband.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public class Assignment {

    private SundayService service;
    private Role role; // Now an enum

    @PlanningVariable(valueRangeProviderRefs = "musicianRange")
    private Musician musician;

    public Assignment() {
        // Required by Timefold
    }

    public Assignment(SundayService service, Role role) {
        this.service = service;
        this.role = role;
    }

    public SundayService getService() {
        return service;
    }

    public void setService(SundayService service) {
        this.service = service;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Musician getMusician() {
        return musician;
    }

    public void setMusician(Musician musician) {
        this.musician = musician;
    }

    @Override
    public String toString() {
        return "Assignment{" +
                "service=" + service +
                ", role=" + role +
                ", musician=" + (musician != null ? musician.getName() : "Unassigned") +
                '}';
    }
}