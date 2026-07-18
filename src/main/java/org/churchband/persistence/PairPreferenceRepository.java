package org.churchband.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for PairPreferenceEntity. There's no natural "find by X"
 * query needed yet — App.java will just call findAll() to get every pair
 * preference, the same way RosterCsv.loadPairPreferencesCsv() returned
 * the whole list at once.
 */
public interface PairPreferenceRepository extends JpaRepository<PairPreferenceEntity, Long> {
}