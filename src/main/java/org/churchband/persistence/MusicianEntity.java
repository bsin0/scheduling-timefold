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

    // Human-chosen id (e.g. "adrian_d"), same ids used in your old CSVs.
    // Using this as the primary key keeps pair_preferences and blockouts
    // referencing something readable instead of an auto-generated number.
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    // Roles stored as a separate table (musician_roles) with one row per
    // role, e.g. ("adrian_d", "GUITARIST"). @ElementCollection handles this
    // automatically — no need to hand-write a join table entity.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "musician_roles", joinColumns = @JoinColumn(name = "musician_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Set<Role> roles = new LinkedHashSet<>();

    // Null/absent means no limit (matches Musician.getMaxWeeksPerMonth()'s
    // Integer.MAX_VALUE convention, but we store null in the DB rather than
    // a magic number, since that's clearer in a database context).
    @Column(name = "max_weeks_per_month")
    private Integer maxWeeksPerMonth;

    // JPA requires a no-arg constructor to construct entities via reflection.
    protected MusicianEntity() {
    }

    public MusicianEntity(String id, String name, Set<Role> roles, Integer maxWeeksPerMonth) {
        this.id = id;
        this.name = name;
        this.roles = roles;
        this.maxWeeksPerMonth = maxWeeksPerMonth;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }

    public Integer getMaxWeeksPerMonth() { return maxWeeksPerMonth; }
    public void setMaxWeeksPerMonth(Integer maxWeeksPerMonth) { this.maxWeeksPerMonth = maxWeeksPerMonth; }
}