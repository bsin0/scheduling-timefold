package org.churchband.persistence;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One blocked (unavailable) date for one musician.
 *
 * Unlike the old CSV format, this table has NO concept of date ranges
 * ("2026-08-03..2026-08-17"). Ranges were a CSV convenience for humans
 * editing text — in the database, we always store one row per individual
 * blocked date. Whoever writes a range in via the frontend (a date-range
 * picker) will have it expanded into individual rows before saving, the
 * same way RosterCsv.parseBlockedDates() expanded ranges when reading CSV.
 *
 * This keeps the entity simple and keeps "how did this blockout arrive"
 * (single date vs range vs recurring) entirely a frontend/API concern,
 * not something the database needs to understand.
 */
@Entity
@Table(
        name = "blockouts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"musician_id", "blocked_date"})
)
public class BlockoutEntity {

    // Auto-generated numeric id — unlike MusicianEntity, there's no natural
    // human-readable key for a single blockout row, so we let the database
    // assign one.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "musician_id", nullable = false)
    private String musicianId;

    @Column(name = "blocked_date", nullable = false)
    private LocalDate blockedDate;

    protected BlockoutEntity() {
        // required by JPA
    }

    public BlockoutEntity(String musicianId, LocalDate blockedDate) {
        this.musicianId = musicianId;
        this.blockedDate = blockedDate;
    }

    public Long getId() { return id; }

    public String getMusicianId() { return musicianId; }
    public void setMusicianId(String musicianId) { this.musicianId = musicianId; }

    public LocalDate getBlockedDate() { return blockedDate; }
    public void setBlockedDate(LocalDate blockedDate) { this.blockedDate = blockedDate; }
}