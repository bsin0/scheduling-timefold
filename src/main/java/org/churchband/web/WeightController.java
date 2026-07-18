package org.churchband.web;

import java.util.List;
import java.util.stream.Collectors;

import org.churchband.persistence.WeightEntity;
import org.churchband.service.WeightService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for viewing and tuning constraint weights.
 *
 * GET /api/weights          - list every weight with its current value + description
 * PUT /api/weights/{name}   - update one weight's value
 *
 * Changes take effect on the NEXT solve — RosterService.solve() reads
 * current values from the database each time it runs (see
 * RosterService.solve() and ConstraintWeights.java).
 */
@RestController
@RequestMapping("/api/weights")
public class WeightController {

    private final WeightService weightService;

    public WeightController(WeightService weightService) {
        this.weightService = weightService;
    }

    @GetMapping
    public List<WeightView> listAll() {
        return weightService.listAll().stream()
                .map(WeightView::from)
                .collect(Collectors.toList());
    }

    /**
     * PUT /api/weights/{name}
     * Body: { "value": 6 }
     */
    @PutMapping("/{name}")
    public ResponseEntity<WeightView> update(@PathVariable String name, @RequestBody UpdateWeightRequest request) {
        try {
            WeightEntity updated = weightService.update(name, request.value());
            return ResponseEntity.ok(WeightView.from(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    public record WeightView(String name, int value, String description) {
        static WeightView from(WeightEntity entity) {
            return new WeightView(entity.getName(), entity.getValue(), entity.getDescription());
        }
    }

    public record UpdateWeightRequest(int value) {
    }
}