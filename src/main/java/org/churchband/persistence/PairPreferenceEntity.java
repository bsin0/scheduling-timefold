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
 * Mirrors pairs.csv: first_id, second_id, type. We store musician ids
 * as plain strings rather than @ManyToOne relationships to MusicianEntity
 * — this keeps writes simple (no need to load both musicians just to
 * record a preference) and matches how the rest of this app already
 * treats musician ids as the natural reference key.
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

    protected PairPreferenceEntity() {
        // required by JPA
    }

    public PairPreferenceEntity(String firstMusicianId, String secondMusicianId, PairPreferenceType type) {
        this.firstMusicianId = firstMusicianId;
        this.secondMusicianId = secondMusicianId;
        this.type = type;
    }

    public Long getId() { return id; }

    public String getFirstMusicianId() { return firstMusicianId; }
    public void setFirstMusicianId(String firstMusicianId) { this.firstMusicianId = firstMusicianId; }

    public String getSecondMusicianId() { return secondMusicianId; }
    public void setSecondMusicianId(String secondMusicianId) { this.secondMusicianId = secondMusicianId; }

    public PairPreferenceType getType() { return type; }
    public void setType(PairPreferenceType type) { this.type = type; }
}