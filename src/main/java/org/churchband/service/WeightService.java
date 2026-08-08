package org.churchband.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.churchband.persistence.WeightEntity;
import org.churchband.persistence.WeightRepository;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Manages constraint weights: seeds sensible defaults the first time the
 * app runs (so it works out of the box without manual setup), and
 * provides both a typed lookup for ScheduleConstraintProvider and a
 * plain list for the REST API / frontend.
 */
@Service
public class WeightService {

    // Default values — these match exactly what used to be hardcoded
    // constants in ScheduleConstraintProvider. If the constraint_weights
    // table is empty (first run), these are inserted once as starting
    // points; after that, the database is the source of truth and this
    // map is never consulted again.
    private static final Map<String, DefaultWeight> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("OVERBOOKING_PENALTY", new DefaultWeight(1,
                "Soft penalty per assignment beyond the overbooking threshold."));
        DEFAULTS.put("WORKLOAD_BALANCE_PENALTY", new DefaultWeight(2,
                "Soft penalty per assignment away from the ideal assignments-per-musician."));
        DEFAULTS.put("DIVERSITY_PENALTY", new DefaultWeight(3,
                "Soft penalty for repeatedly picking an already-used musician."));
        DEFAULTS.put("CONSECUTIVE_SERVICE_PENALTY", new DefaultWeight(3,
                "Soft penalty when the same musician serves two Sundays in a row."));
        DEFAULTS.put("MULTI_ROLE_REWARD", new DefaultWeight(1,
                "Soft reward for allowed non-vocalist role doubling (e.g. Band Director + Guitarist)."));
        DEFAULTS.put("VOCALIST_DOUBLE_UP_REWARD", new DefaultWeight(4,
                "Soft reward for an instrumentalist also covering backup vocals."));
        DEFAULTS.put("SECOND_VOCALIST_REWARD", new DefaultWeight(4,
                "Soft reward for filling VOCALIST_2 with a qualified singer."));
        DEFAULTS.put("THIRD_VOCALIST_REWARD", new DefaultWeight(1,
                "Soft reward for filling VOCALIST_3 with a qualified singer."));
        DEFAULTS.put("COUPLE_PREFER_TOGETHER_SOFT_PENALTY", new DefaultWeight(2,
                "Soft penalty when a \"nice to serve together\" couple is split up."));
        DEFAULTS.put("COUPLE_PREFER_TOGETHER_STRONG_PENALTY", new DefaultWeight(10,
                "Soft penalty when a \"strongly prefer together\" couple is split up (e.g. one-car households)."));
    }

    private record DefaultWeight(int value, String description) {
    }

    private final WeightRepository weightRepository;

    public WeightService(WeightRepository weightRepository) {
        this.weightRepository = weightRepository;
    }

    // Runs once, right after this bean is constructed at app startup —
    // checks if weights already exist (normal case, after first run) and
    // only inserts defaults if the table is genuinely empty.
    @PostConstruct
    public void seedDefaultsIfEmpty() {
        if (weightRepository.count() > 0) {
            return;
        }
        DEFAULTS.forEach((name, def) ->
                weightRepository.save(new WeightEntity(name, def.value(), def.description())));
    }

    /** All weights, for the REST API / frontend to display and edit. */
    public List<WeightEntity> listAll() {
        return weightRepository.findAll();
    }

    /**
     * Returns each weight's default value, keyed by name — used by the
     * REST layer to include "here's what this resets to" alongside the
     * current value, without a separate round trip per weight.
     */
    public Map<String, Integer> defaultValuesByName() {
        Map<String, Integer> out = new LinkedHashMap<>();
        DEFAULTS.forEach((name, def) -> out.put(name, def.value()));
        return out;
    }

    /**
     * Typed lookup used by ScheduleConstraintProvider at solve time.
     * Falls back to the coded default if a weight is somehow missing
     * from the database (shouldn't normally happen after seeding, but
     * keeps solving from breaking outright if a row gets deleted).
     */
    public int get(String name) {
        return weightRepository.findById(name)
                .map(WeightEntity::getValue)
                .orElseGet(() -> {
                    DefaultWeight fallback = DEFAULTS.get(name);
                    if (fallback == null) {
                        throw new IllegalArgumentException("Unknown weight name: " + name);
                    }
                    return fallback.value();
                });
    }

    /** Updates one weight's value. Throws if the name doesn't exist. */
    public WeightEntity update(String name, int newValue) {
        WeightEntity entity = weightRepository.findById(name)
                .orElseThrow(() -> new IllegalArgumentException("Unknown weight name: " + name));
        entity.setValue(newValue);
        return weightRepository.save(entity);
    }

    /**
     * The coded default value for a weight — what it was seeded with on
     * first run. Used by the "reset to default" feature; kept separate
     * from the current live value in the database, which may have since
     * been changed.
     */
    public int getDefaultValue(String name) {
        DefaultWeight fallback = DEFAULTS.get(name);
        if (fallback == null) {
            throw new IllegalArgumentException("Unknown weight name: " + name);
        }
        return fallback.value();
    }

    /** Resets one weight back to its coded default value. */
    public WeightEntity resetToDefault(String name) {
        return update(name, getDefaultValue(name));
    }

    /** Resets every weight back to its coded default value. */
    public List<WeightEntity> resetAllToDefaults() {
        DEFAULTS.forEach((name, def) ->
                weightRepository.findById(name).ifPresent(entity -> {
                    entity.setValue(def.value());
                    weightRepository.save(entity);
                }));
        return listAll();
    }
}