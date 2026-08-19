package org.churchband.persistence;

import java.util.LinkedHashSet;
import java.util.Set;

import org.churchband.domain.Role;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * Database-backed representation of a musician.
 *
 * This is intentionally a SEPARATE class from org.churchband.domain.Musician.
 * Musician (the domain object) is immutable and used by the Timefold solver —
 * we don't want JPA's reflection-based construction touching that class.
 * MusicianEntity is what Spring Data JPA reads/writes to the database; we
 * convert MusicianEntity -> Musician when loading data for a solve.
 */
@Entity
@Table(name = "musicians")
public class MusicianEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "musician_roles", joinColumns = @JoinColumn(name = "musician_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Set<Role> roles = new LinkedHashSet<>();

    @Column(name = "max_weeks_per_month")
    private Integer maxWeeksPerMonth;

    @Column(name = "excluded", nullable = false, columnDefinition = "boolean default false")
    private boolean excluded;

    protected MusicianEntity() {
    }

    public MusicianEntity(String id, String name, Set<Role> roles, Integer maxWeeksPerMonth, boolean excluded) {
        this.id = id;
        this.name = name;
        this.roles = roles;
        this.maxWeeksPerMonth = maxWeeksPerMonth;
        // FIX: this line was missing — the excluded parameter was being
        // accepted but never actually assigned to the field, so every
        // musician created via this constructor silently got
        // excluded=false regardless of what the caller passed in. This
        // meant the exclude/include toggle likely appeared to work in
        // the UI (the request succeeded) but never actually took effect
        // on newly-created musicians going through this constructor
        // path — e.g. via MusicianController.create().
        this.excluded = excluded;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }

    public Integer getMaxWeeksPerMonth() { return maxWeeksPerMonth; }
    public void setMaxWeeksPerMonth(Integer maxWeeksPerMonth) { this.maxWeeksPerMonth = maxWeeksPerMonth; }

    public boolean isExcluded() { return excluded; }
    public void setExcluded(boolean excluded) { this.excluded = excluded; }
}