package org.churchband.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.churchband.domain.Role;
import org.churchband.persistence.BlockoutEntity;
import org.churchband.persistence.BlockoutRepository;
import org.churchband.persistence.MusicianEntity;
import org.churchband.persistence.MusicianRepository;
import org.churchband.persistence.PairPreferenceEntity;
import org.churchband.persistence.PairPreferenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for managing musicians and their blocked (unavailable)
 * dates. Separate from RosterController on purpose: this controller is
 * about MANAGING roster data (musicians, blockouts); RosterController is
 * about RUNNING the solver. Different concerns, different classes.
 */
@RestController
@RequestMapping("/api/musicians")
public class MusicianController {

    private final MusicianRepository musicianRepository;
    private final BlockoutRepository blockoutRepository;
    private final PairPreferenceRepository pairPreferenceRepository;

    public MusicianController(MusicianRepository musicianRepository, BlockoutRepository blockoutRepository,
                               PairPreferenceRepository pairPreferenceRepository) {
        this.musicianRepository = musicianRepository;
        this.blockoutRepository = blockoutRepository;
        this.pairPreferenceRepository = pairPreferenceRepository;
    }

    // ---- Musician CRUD ----

    /** GET /api/musicians — list everyone, with their blocked dates attached. */
    @GetMapping
    public List<MusicianView> listAll() {
        return musicianRepository.findAll().stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    /** GET /api/musicians/{id} — a single musician, 404 if not found. */
    @GetMapping("/{id}")
    public ResponseEntity<MusicianView> getOne(@PathVariable String id) {
        return musicianRepository.findById(id)
                .map(entity -> ResponseEntity.ok(toView(entity)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/musicians — create a new musician.
     * Body: { "id": "adrian_d", "name": "Adrian D", "roles": ["GUITARIST"], "maxWeeksPerMonth": null, "excluded": false }
     */
    @PostMapping
    public ResponseEntity<MusicianView> create(@RequestBody MusicianRequest request) {
        if (musicianRepository.existsById(request.id())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        MusicianEntity entity = new MusicianEntity(
                request.id(), request.name(), request.roles(), request.maxWeeksPerMonth(), request.excluded());
        musicianRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(entity));
    }

    /**
     * PUT /api/musicians/{id} — update an existing musician's name, roles,
     * excluded status, or max weeks per month. Does NOT touch blockouts —
     * use the separate blockout endpoints below for those.
     *
     * BLOCKING RULE: if this request would set excluded=true on a
     * musician who currently has any ENABLED pair preference (with
     * anyone), the update is rejected with 409 Conflict and a message
     * naming the blocking preference(s). This is deliberate — rather
     * than silently dropping the preference or letting a later solve
     * crash (see RosterService.loadPairPreferences()), the admin is
     * forced to consciously disable the relevant pair preference(s)
     * first (via PairPreferenceController's enable/disable toggle),
     * then retry the exclude.
     */
    @Transactional
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody MusicianRequest request) {
        return musicianRepository.findById(id)
                .<ResponseEntity<?>>map(entity -> {
                    boolean isNewlyExcluding = request.excluded() && !entity.isExcluded();
                    if (isNewlyExcluding) {
                        List<PairPreferenceEntity> blocking = pairPreferenceRepository.findAll().stream()
                                .filter(PairPreferenceEntity::isEnabled)
                                .filter(pp -> pp.getFirstMusicianId().equals(id) || pp.getSecondMusicianId().equals(id))
                                .collect(Collectors.toList());
                        if (!blocking.isEmpty()) {
                            List<String> describedPairs = blocking.stream()
                                    .map(pp -> describePair(pp, id))
                                    .collect(Collectors.toList());
                            return ResponseEntity.status(HttpStatus.CONFLICT)
                                    .body(new ExcludeBlockedResponse(
                                            "Cannot exclude " + entity.getName()
                                                    + " — disable the following pair preference(s) first:",
                                            describedPairs));
                        }
                    }

                    entity.setName(request.name());
                    entity.setRoles(request.roles());
                    entity.setMaxWeeksPerMonth(request.maxWeeksPerMonth());
                    entity.setExcluded(request.excluded());
                    musicianRepository.save(entity);
                    return ResponseEntity.ok(toView(entity));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private String describePair(PairPreferenceEntity pp, String excludingId) {
        String otherId = pp.getFirstMusicianId().equals(excludingId)
                ? pp.getSecondMusicianId() : pp.getFirstMusicianId();
        String otherName = musicianRepository.findById(otherId)
                .map(MusicianEntity::getName).orElse(otherId);
        return "with " + otherName + " (" + pp.getType() + ", preference id " + pp.getId() + ")";
    }

    /**
     * DELETE /api/musicians/{id} — removes the musician AND their
     * blockouts (deleteByMusicianId first, so we don't leave orphaned
     * blockout rows referencing a musician that no longer exists).
     */
    @Transactional
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!musicianRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        blockoutRepository.deleteByMusicianId(id);
        musicianRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Blockouts ----

    /**
     * POST /api/musicians/{id}/blockouts — add one blocked date.
     * Body: { "date": "2026-07-12" }
     * Or for a range: { "date": "2026-08-03", "endDate": "2026-08-17" }
     * (inclusive — every day from date through endDate is blocked)
     *
     * Adding a date that's already blocked is a harmless no-op (the
     * unique constraint on BlockoutEntity prevents duplicates; we check
     * first rather than relying on a database exception).
     */
    @PostMapping("/{id}/blockouts")
    public ResponseEntity<Void> addBlockout(@PathVariable String id, @RequestBody BlockoutRequest request) {
        if (!musicianRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        LocalDate start = request.date();
        LocalDate end = request.endDate() != null ? request.endDate() : request.date();
        if (end.isBefore(start)) {
            return ResponseEntity.badRequest().build();
        }

        Set<LocalDate> alreadyBlocked = blockoutRepository.findByMusicianId(id).stream()
                .map(BlockoutEntity::getBlockedDate)
                .collect(Collectors.toSet());

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (!alreadyBlocked.contains(d)) {
                blockoutRepository.save(new BlockoutEntity(id, d));
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** GET /api/musicians/{id}/blockouts — list one musician's blocked dates. */
    @GetMapping("/{id}/blockouts")
    public ResponseEntity<List<LocalDate>> listBlockouts(@PathVariable String id) {
        if (!musicianRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        List<LocalDate> dates = blockoutRepository.findByMusicianId(id).stream()
                .map(BlockoutEntity::getBlockedDate)
                .sorted()
                .collect(Collectors.toList());
        return ResponseEntity.ok(dates);
    }

    /**
     * DELETE /api/musicians/{id}/blockouts/{date} — remove a single
     * blocked date (e.g. someone's plans changed and they're free again).
     */
    @DeleteMapping("/{id}/blockouts/{date}")
    public ResponseEntity<Void> removeBlockout(@PathVariable String id, @PathVariable LocalDate date) {
        List<BlockoutEntity> matches = blockoutRepository.findByMusicianId(id).stream()
                .filter(b -> b.getBlockedDate().equals(date))
                .collect(Collectors.toList());
        if (matches.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        blockoutRepository.deleteAll(matches);
        return ResponseEntity.noContent().build();
    }

    // ---- Conversion + request/response shapes ----

    private MusicianView toView(MusicianEntity entity) {
        List<LocalDate> blockedDates = blockoutRepository.findByMusicianId(entity.getId()).stream()
                .map(BlockoutEntity::getBlockedDate)
                .sorted()
                .collect(Collectors.toList());
        return new MusicianView(entity.getId(), entity.getName(), entity.getRoles(),
                entity.getMaxWeeksPerMonth(), blockedDates, entity.isExcluded());
    }

    public record MusicianView(String id, String name, Set<Role> roles,
                                Integer maxWeeksPerMonth, List<LocalDate> blockedDates,
                                boolean excluded) {
    }

    public record MusicianRequest(String id, String name, Set<Role> roles, Integer maxWeeksPerMonth, boolean excluded) {
    }

    public record BlockoutRequest(LocalDate date, LocalDate endDate) {
    }

    public record ExcludeBlockedResponse(String message, List<String> blockingPairPreferences) {
    }
}