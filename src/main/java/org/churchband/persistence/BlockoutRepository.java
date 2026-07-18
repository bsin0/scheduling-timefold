package org.churchband.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for BlockoutEntity. Primary key is Long (BlockoutEntity's
 * auto-generated id), so JpaRepository<BlockoutEntity, Long>.
 */
public interface BlockoutRepository extends JpaRepository<BlockoutEntity, Long> {

    // Get every blocked date for one musician — this is the main query
    // App.java will need when building Musician objects for a solve.
    List<BlockoutEntity> findByMusicianId(String musicianId);

    // Useful for a "clear my blockouts and resubmit" flow in the future
    // frontend, so a musician can replace their whole blockout list in
    // one action rather than deleting rows one at a time.
    void deleteByMusicianId(String musicianId);
}