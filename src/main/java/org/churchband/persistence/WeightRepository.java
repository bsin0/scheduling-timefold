package org.churchband.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WeightRepository extends JpaRepository<WeightEntity, String> {
}