
package org.churchband.util;

import org.churchband.domain.Musician;
import org.churchband.domain.PairPreference;
import org.churchband.domain.PairPreferenceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Fast, ID-based roster index with CSV loading for pair preferences.
 * - Indexes musicians by stable id.
 * - Loads PairPreference from an ID-based CSV: first_id,second_id,type.
 * - Provides a validator that reports unknown ids/types before solving.
 */
public final class RosterIndex {

    private final Map<String, Musician> byId;

    private RosterIndex(Map<String, Musician> byId) {
        this.byId = byId;
    }

    /** Build ID-based index from the roster. */
    public static RosterIndex of(List<Musician> musicians) {
        Map<String, Musician> idMap = new LinkedHashMap<>();
        for (Musician m : musicians) {
            idMap.put(m.getId(), m);
        }
        return new RosterIndex(idMap);
    }

    /** Get a musician by ID (throws with a helpful message if not found). */
    public Musician getById(String id) {
        Musician m = byId.get(id);
        if (m == null) {
            String available = byId.keySet().stream().sorted().collect(Collectors.joining(", "));
            throw new IllegalArgumentException("No musician with id: \"" + id + "\". Available ids: " + available);
        }
        return m;
    }

    /** Load PairPreference rows from an ID-based CSV: first_id,second_id,type */
    public List<PairPreference> loadPairPreferencesCsv(Path csvPath) throws IOException {
        List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        if (lines.isEmpty()) return Collections.emptyList();

        // Validate header
        String header = lines.get(0).trim().toLowerCase(Locale.ROOT);
        if (!(header.contains("first_id") && header.contains("second_id") && header.contains("type"))) {
            throw new IllegalArgumentException("CSV header must contain: first_id, second_id, type. Got: " + header);
        }

        // Build header index map
        String[] hdrCols = lines.get(0).split(",", -1);
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < hdrCols.length; i++) {
            idx.put(hdrCols[i].trim().toLowerCase(Locale.ROOT), i);
        }
        int firstIdx  = idx.getOrDefault("first_id", -1);
        int secondIdx = idx.getOrDefault("second_id", -1);
        int typeIdx   = idx.getOrDefault("type", -1);
        if (firstIdx < 0 || secondIdx < 0 || typeIdx < 0) {
            throw new IllegalArgumentException("CSV header indices not found: " + idx);
        }

        List<PairPreference> out = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            String aId = cols[firstIdx].trim();
            String bId = cols[secondIdx].trim();
            String typeStr = cols[typeIdx].trim();

            Musician mA = getById(aId);
            Musician mB = getById(bId);
            PairPreferenceType type = PairPreferenceType.valueOf(typeStr);

            out.add(new PairPreference(mA, mB, type));
        }
        return out;
    }

    /**
     * Validate an ID-based pairs CSV and return human-friendly issues (does not throw).
     * You can choose to abort or continue based on the returned list.
     */
    public List<String> validatePairCsvIds(Path csvPath) {
        List<String> issues = new ArrayList<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            issues.add("Cannot read CSV at: " + csvPath + " → " + e.getMessage());
            return issues;
        }
        if (lines.isEmpty()) {
            issues.add("CSV is empty: " + csvPath);
            return issues;
        }

        String header = lines.get(0).trim().toLowerCase(Locale.ROOT);
        if (!(header.contains("first_id") && header.contains("second_id") && header.contains("type"))) {
            issues.add("CSV header must contain: first_id, second_id, type. Got: " + header);
            return issues;
        }

        String[] hdrCols = lines.get(0).split(",", -1);
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < hdrCols.length; i++) {
            idx.put(hdrCols[i].trim().toLowerCase(Locale.ROOT), i);
        }
        int firstIdx  = idx.getOrDefault("first_id", -1);
        int secondIdx = idx.getOrDefault("second_id", -1);
        int typeIdx   = idx.getOrDefault("type", -1);
        if (firstIdx < 0 || secondIdx < 0 || typeIdx < 0) {
            issues.add("CSV header indices not found: " + idx);
            return issues;
        }

        Set<String> validTypes = Set.of(
                "NOT_TOGETHER_SAME_SERVICE_HARD",
                "PREFER_TOGETHER_SAME_SERVICE_SOFT"
        );

        int rowNum = 1;
        for (String line : lines.subList(1, lines.size())) {
            rowNum++;
            if (line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            if (cols.length <= Math.max(Math.max(firstIdx, secondIdx), typeIdx)) {
                issues.add("Row " + rowNum + ": too few columns: " + Arrays.toString(cols));
                continue;
            }

            String aId = cols[firstIdx].trim();
            String bId = cols[secondIdx].trim();
            String typeStr = cols[typeIdx].trim();

            if (!byId.containsKey(aId)) {
                issues.add("Row " + rowNum + ": unknown first_id \"" + aId + "\"");
            }
            if (!byId.containsKey(bId)) {
                issues.add("Row " + rowNum + ": unknown second_id \"" + bId + "\"");
            }
            if (!validTypes.contains(typeStr)) {
                issues.add("Row " + rowNum + ": invalid type \"" + typeStr + "\". Valid: " + validTypes);
            }
            if (aId.equals(bId)) {
                issues.add("Row " + rowNum + ": first_id equals second_id → \"" + aId + "\"");
            }
        }
        return issues;
    }

    /** Optional quick debug: print known IDs. */
    public void printRosterIds() {
        System.out.println("Roster IDs:");
        byId.keySet().stream().sorted().forEach(id -> System.out.println(" - " + id));
    }
}
