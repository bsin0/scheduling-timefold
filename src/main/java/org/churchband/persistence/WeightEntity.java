package org.churchband.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One tunable constraint weight (e.g. "SECOND_VOCALIST_REWARD" -> 4).
 *
 * This replaces the private static final int constants that used to
 * live directly in ScheduleConstraintProvider. Moving them here means
 * they can be read and changed at runtime via the API/frontend, and
 * persist across app restarts — same pattern as musicians and blockouts.
 *
 * The "name" field's values match the constant names from
 * ScheduleConstraintProvider exactly (e.g. "OVERBOOKING_PENALTY",
 * "SECOND_VOCALIST_REWARD") so the mapping between "what this number
 * means in code" and "what this number means in the database" stays
 * obvious.
 */
@Entity
@Table(name = "constraint_weights")
public class WeightEntity {

    @Id
    private String name;

    @Column(name = "weight_value", nullable = false)
    private int value;

    // A short, human-readable explanation shown in the frontend, so
    // whoever's tuning weights doesn't need to go read Java source to
    // understand what "DIVERSITY_PENALTY" actually affects.
    @Column(length = 500)
    private String description;

    protected WeightEntity() {
        // required by JPA
    }

    public WeightEntity(String name, int value, String description) {
        this.name = name;
        this.value = value;
        this.description = description;
    }

    public String getName() { return name; }

    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}