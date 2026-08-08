package org.churchband.web;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.churchband.persistence.WeightEntity;
import org.churchband.service.WeightService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for viewing, tuning, and resetting constraint weights.
 *
 * GET  /api/weights                - list every weight: current value,
 *                                     default value, and description
 * PUT  /api/weights/{name}         - update one weight's value
 * POST /api/weights/{name}/reset   - reset one weight back to its default
 * POST /api/weights/reset-all      - reset every weight back to defaults
 *
 * Changes take effect on the NEXT solve — RosterService.solve() and
 * LiveSolveService read current values from the database each time they
 * run (see ConstraintWeights.java).
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
        Map<String, Integer> defaults = weightService.defaultValuesByName();
        return weightService.listAll().stream()
                .map(entity -> WeightView.from(entity, defaults.get(entity.getName())))
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
            int defaultValue = weightService.getDefaultValue(name);
            return ResponseEntity.ok(WeightView.from(updated, defaultValue));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** POST /api/weights/{name}/reset — reset one weight to its default value. */
    @PostMapping("/{name}/reset")
    public ResponseEntity<WeightView> resetOne(@PathVariable String name) {
        try {
            WeightEntity reset = weightService.resetToDefault(name);
            int defaultValue = weightService.getDefaultValue(name);
            return ResponseEntity.ok(WeightView.from(reset, defaultValue));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** POST /api/weights/reset-all — reset every weight to its default value. */
    @PostMapping("/reset-all")
    public List<WeightView> resetAll() {
        Map<String, Integer> defaults = weightService.defaultValuesByName();
        return weightService.resetAllToDefaults().stream()
                .map(entity -> WeightView.from(entity, defaults.get(entity.getName())))
                .collect(Collectors.toList());
    }

    public record WeightView(String name, int value, int defaultValue, boolean isDefault, String description) {
        static WeightView from(WeightEntity entity, Integer defaultValue) {
            int def = defaultValue != null ? defaultValue : entity.getValue();
            return new WeightView(entity.getName(), entity.getValue(), def,
                    entity.getValue() == def, entity.getDescription());
        }
    }

    public record UpdateWeightRequest(int value) {
    }
}