
package org.churchband.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

public final class Musician {

    private final String id;                // stable identifier (e.g., "joel_o")
    private final String name;              // display name (e.g., "Joel O")
    private final Set<Role> roles;          // capabilities
    private final Set<LocalDate> availableDates;

    /** Preferred constructor: explicit ID is stable even if display name changes. */
    public Musician(String id, String name, Set<Role> roles, Set<LocalDate> availableDates) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.roles = Objects.requireNonNull(roles, "roles");
        this.availableDates = Objects.requireNonNull(availableDates, "availableDates");
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Set<Role> getRoles() { return roles; }
    public Set<LocalDate> getAvailableDates() { return availableDates; }

    // Used by constraints:
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
