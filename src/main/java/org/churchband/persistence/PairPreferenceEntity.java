package org.churchband.persistence;

import org.churchband.domain.PairPreferenceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Database-backed representation of a pair preference between two
 * musicians (e.g. a couple who'd like to serve together, or apart).
 *
 * The `enabled` field lets an admin disable a pair preference without
 * deleting it — most importantly, this is what allows excluding a
 * musician who has active pair preferences: rather than silently
 * dropping the preference or crashing the solve, the exclude action
 * is BLOCKED until any enabled pair preferences referencing that
 * musician are explicitly disabled first (see MusicianController and
 * RosterService.loadPairPreferences()). This keeps the admin aware of
 * the conflict rather than hiding it.
 */
@Entity
@Table(name = "pair_preferences")
public class PairPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_musician_id", nullable = false)
    private String firstMusicianId;

    @Column(name = "second_musician_id", nullable = false)
    private String secondMusicianId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PairPreferenceType type;

    @Column(name = "enabled", nullable = false, columnDefinition = "boolean default true")
    private boolean enabled = true;

    protected PairPreferenceEntity() {
        // required by JPA
    }

    public PairPreferenceEntity(String firstMusicianId, String secondMusicianId, PairPreferenceType type) {
        this.firstMusicianId = firstMusicianId;
        this.secondMusicianId = secondMusicianId;
        this.type = type;
        this.enabled = true;
    }

    public Long getId() { return id; }

    public String getFirstMusicianId() { return firstMusicianId; }
    public void setFirstMusicianId(String firstMusicianId) { this.firstMusicianId = firstMusicianId; }

    public String getSecondMusicianId() { return secondMusicianId; }
    public void setSecondMusicianId(String secondMusicianId) { this.secondMusicianId = secondMusicianId; }

    public PairPreferenceType getType() { return type; }
    public void setType(PairPreferenceType type) { this.type = type; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}