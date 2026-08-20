package org.churchband.web;

import java.util.List;
import java.util.stream.Collectors;

import org.churchband.domain.PairPreferenceType;
import org.churchband.persistence.MusicianRepository;
import org.churchband.persistence.PairPreferenceEntity;
import org.churchband.persistence.PairPreferenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for managing pair preferences — couples (or any pair)
 * who should or shouldn't serve on the same Sunday.
 */
@RestController
@RequestMapping("/api/pair-preferences")
public class PairPreferenceController {

    private final PairPreferenceRepository pairPreferenceRepository;
    private final MusicianRepository musicianRepository;

    public PairPreferenceController(PairPreferenceRepository pairPreferenceRepository,
                                     MusicianRepository musicianRepository) {
        this.pairPreferenceRepository = pairPreferenceRepository;
        this.musicianRepository = musicianRepository;
    }

    /** GET /api/pair-preferences — list all, with musician names resolved for display. */
    @GetMapping
    public List<PairPreferenceView> listAll() {
        return pairPreferenceRepository.findAll().stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    /**
     * POST /api/pair-preferences
     * Body: { "firstMusicianId": "adrian_d", "secondMusicianId": "sarah_l",
     *         "type": "PREFER_TOGETHER_SAME_SERVICE_STRONG" }
     * New pair preferences are always created enabled.
     */
    @PostMapping
    public ResponseEntity<PairPreferenceView> create(@RequestBody PairPreferenceRequest request) {
        if (request.firstMusicianId().equals(request.secondMusicianId())) {
            return ResponseEntity.badRequest().build();
        }
        if (!musicianRepository.existsById(request.firstMusicianId())
                || !musicianRepository.existsById(request.secondMusicianId())) {
            return ResponseEntity.badRequest().build();
        }
        PairPreferenceEntity entity = new PairPreferenceEntity(
                request.firstMusicianId(), request.secondMusicianId(), request.type());
        pairPreferenceRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(entity));
    }

    /** PUT /api/pair-preferences/{id} — change the type of an existing pair (e.g. soft → strong). */
    @PutMapping("/{id}")
    public ResponseEntity<PairPreferenceView> update(@PathVariable Long id, @RequestBody PairPreferenceRequest request) {
        return pairPreferenceRepository.findById(id)
                .map(entity -> {
                    entity.setFirstMusicianId(request.firstMusicianId());
                    entity.setSecondMusicianId(request.secondMusicianId());
                    entity.setType(request.type());
                    pairPreferenceRepository.save(entity);
                    return ResponseEntity.ok(toView(entity));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * PUT /api/pair-preferences/{id}/enabled — toggle enable/disable.
     * Body: { "enabled": false }
     *
     * This is the mechanism that unblocks excluding a musician who has
     * active pair preferences (see MusicianController.update()) — an
     * admin disables the preference here first, which then also makes
     * it invisible to the solver (RosterService.loadPairPreferences()
     * filters to enabled-only), before the exclude action is retried.
     */
    @PutMapping("/{id}/enabled")
    public ResponseEntity<PairPreferenceView> setEnabled(@PathVariable Long id, @RequestBody SetEnabledRequest request) {
        return pairPreferenceRepository.findById(id)
                .map(entity -> {
                    entity.setEnabled(request.enabled());
                    pairPreferenceRepository.save(entity);
                    return ResponseEntity.ok(toView(entity));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** DELETE /api/pair-preferences/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!pairPreferenceRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        pairPreferenceRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private PairPreferenceView toView(PairPreferenceEntity entity) {
        String firstName = musicianRepository.findById(entity.getFirstMusicianId())
                .map(m -> m.getName()).orElse(entity.getFirstMusicianId());
        String secondName = musicianRepository.findById(entity.getSecondMusicianId())
                .map(m -> m.getName()).orElse(entity.getSecondMusicianId());
        return new PairPreferenceView(entity.getId(), entity.getFirstMusicianId(), firstName,
                entity.getSecondMusicianId(), secondName, entity.getType(), entity.isEnabled());
    }

    public record PairPreferenceView(Long id, String firstMusicianId, String firstMusicianName,
                                      String secondMusicianId, String secondMusicianName,
                                      PairPreferenceType type, boolean enabled) {
    }

    public record PairPreferenceRequest(String firstMusicianId, String secondMusicianId, PairPreferenceType type) {
    }

    public record SetEnabledRequest(boolean enabled) {
    }
}