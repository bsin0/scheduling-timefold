package org.churchband.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

public final class Musician {

    private final String id;
    private final String name;
    private final Set<Role> roles;

    // Blockouts, not an allow-list. Musicians are available by default;
    // this set holds the specific dates they've said they CAN'T serve.
    // This matches how people actually think ("I'm out on the 12th"),
    // and means a musician who joins mid-schedule or forgets to update
    // their dates defaults to available rather than silently excluded.
    private final Set<LocalDate> blockedDates;

    private final int maxWeeksPerMonth; // Integer.MAX_VALUE means no limit

    public Musician(String id, String name, Set<Role> roles, Set<LocalDate> blockedDates, int maxWeeksPerMonth) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.roles = Objects.requireNonNull(roles, "roles");
        this.blockedDates = Objects.requireNonNull(blockedDates, "blockedDates");
        this.maxWeeksPerMonth = maxWeeksPerMonth;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Set<Role> getRoles() { return roles; }
    public Set<LocalDate> getBlockedDates() { return blockedDates; }
    public int getMaxWeeksPerMonth() { return maxWeeksPerMonth; }

    public boolean isAvailableOn(LocalDate date) {
        return !blockedDates.contains(date);
    }

    public boolean canPerformRole(Role role) {
        // VOCALIST_2 and VOCALIST_3 are optional backup vocalist slots, not
        // distinct skills. Rather than requiring musicians.csv to tag people
        // with three separate role labels, anyone qualified as VOCALIST is
        // automatically treated as qualified for VOCALIST_2 and VOCALIST_3 too.
        if (role == Role.VOCALIST_2 || role == Role.VOCALIST_3) {
            return roles.contains(Role.VOCALIST);
        }
        return roles.contains(role);
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