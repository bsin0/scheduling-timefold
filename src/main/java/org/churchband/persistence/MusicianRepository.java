package org.churchband.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for MusicianEntity.
 *
 * You don't write an implementation of this interface — Spring generates
 * one automatically at startup, based on the method names and the fact
 * that it extends JpaRepository. Extending JpaRepository<MusicianEntity, String>
 * already gives you (for free, no code needed):
 *   save(entity), findById(id), findAll(), deleteById(id), count(), ...
 *
 * The <MusicianEntity, String> means: this repository manages
 * MusicianEntity objects, whose @Id field is a String (matches
 * MusicianEntity.id, e.g. "adrian_d").
 */
public interface MusicianRepository extends JpaRepository<MusicianEntity, String> {

    // Example of a "derived query" — Spring Data JPA reads this method
    // name and generates the SQL automatically (SELECT * FROM musicians
    // WHERE name = ?). No @Query annotation or SQL needed for something
    // this simple.
    List<MusicianEntity> findByName(String name);
}