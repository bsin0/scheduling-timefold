package org.churchband.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

public final class Musician {

    private final String id;
    private final String name;
    private final Set<Role> roles;
    private final Set<LocalDate> availableDates;
    private final int maxWeeksPerMonth; // Integer.MAX_VALUE means no limit

    public Musician(String id, String name, Set<Role> roles, Set<LocalDate> availableDates, int maxWeeksPerMonth) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.roles = Objects.requireNonNull(roles, "roles");
        this.availableDates = Objects.requireNonNull(availableDates, "availableDates");
        this.maxWeeksPerMonth = maxWeeksPerMonth;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Set<Role> getRoles() { return roles; }
    public Set<LocalDate> getAvailableDates() { return availableDates; }
    public int getMaxWeeksPerMonth() { return maxWeeksPerMonth; }

    public boolean isAvailableOn(LocalDate date) {
        return availableDates.contains(date);
    }
    public boolean canPerformRole(Role role) {
        return roles.contains(role);
    }
    public boolean isWorshipLeaderCapable() {
        return roles.contains(Role.WORSHIP_LEADER);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Musician)) return false;
        Musician that = (Musician) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}