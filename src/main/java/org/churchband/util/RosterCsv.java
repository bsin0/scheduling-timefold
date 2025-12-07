
package org.churchband.util;

import org.churchband.domain.Musician;
import org.churchband.domain.PairPreference;
import org.churchband.domain.PairPreferenceType;
import org.churchband.domain.Role;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CSV loaders and validators for musicians and pair preferences.
 *
 * musicians.csv columns:
 *   id,name,roles,available_dates
 *   roles and available_dates are semicolon-separated lists.
 *
 * pairs.csv columns (ID-based):
 *   first_id,second_id,type
 *   type ∈ { NOT_TOGETHER_SAME_SERVICE_HARD, PREFER_TOGETHER_SAME_SERVICE_SOFT }
 */
public final class RosterCsv {

    private RosterCsv() {}

    // ---------- Musicians ----------

    public static List<String> validateMusiciansCsv(Path csvPath) {
        List<String> issues = new ArrayList<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            issues.add("Cannot read musicians CSV at " + csvPath + " → " + e.getMessage());
            return issues;
        }
        if (lines.isEmpty()) {
            issues.add("Musicians CSV is empty: " + csvPath);
            return issues;
        }

        Map<String, Integer> idx = headerIndex(lines.get(0), "id", "name", "roles", "available_dates");
        int idIdx = idx.get("id"), nameIdx = idx.get("name"), rolesIdx = idx.get("roles"), datesIdx = idx.get("available_dates");
        Set<String> seenIds = new HashSet<>();

        int row = 1;
        for (String line : lines.subList(1, lines.size())) {
            row++;
            if (line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            if (cols.length <= Math.max(Math.max(idIdx, nameIdx), Math.max(rolesIdx, datesIdx))) {
                issues.add("Row " + row + ": too few columns.");
                continue;
            }
            String id = cols[idIdx].trim();
            String name = cols[nameIdx].trim();
            String rolesStr = cols[rolesIdx].trim();
            String datesStr = cols[datesIdx].trim();

            if (id.isEmpty()) issues.add("Row " + row + ": missing id.");
            if (name.isEmpty()) issues.add("Row " + row + ": missing name.");
            if (!seenIds.add(id)) issues.add("Row " + row + ": duplicate id \"" + id + "\".");

            // Validate roles
            for (String r : rolesStr.split(";", -1)) {
                String role = r.trim();
                if (role.isEmpty()) continue;
                try {
                    Role.valueOf(role);
                } catch (IllegalArgumentException ex) {
                    issues.add("Row " + row + ": unknown role \"" + role + "\".");
                }
            }

            // Validate dates
            for (String d : datesStr.split(";", -1)) {
                String date = d.trim();
                if (date.isEmpty()) continue;
                try {
                    LocalDate.parse(date);
                } catch (Exception ex) {
                    issues.add("Row " + row + ": invalid date \"" + date + "\"; expected ISO yyyy-MM-dd.");
                }
            }
        }
        return issues;
    }

    public static List<Musician> loadMusiciansCsv(Path csvPath) throws IOException {
        List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        if (lines.isEmpty()) return List.of();

        Map<String, Integer> idx = headerIndex(lines.get(0), "id", "name", "roles", "available_dates");
        int idIdx = idx.get("id"), nameIdx = idx.get("name"), rolesIdx = idx.get("roles"), datesIdx = idx.get("available_dates");

        List<Musician> out = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            String id = cols[idIdx].trim();
            String name = cols[nameIdx].trim();

            Set<Role> roles = Arrays.stream(cols[rolesIdx].split(";", -1))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Role::valueOf)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            Set<LocalDate> dates = Arrays.stream(cols[datesIdx].split(";", -1))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(LocalDate::parse)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            out.add(new Musician(id, name, roles, dates));
        }
        return out;
    }

    // ---------- Pair preferences ----------

    public static List<String> validatePairsCsv(Path csvPath, Set<String> knownIds) {
        List<String> issues = new ArrayList<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            issues.add("Cannot read pairs CSV at " + csvPath + " → " + e.getMessage());
            return issues;
        }
        if (lines.isEmpty()) {
            issues.add("Pairs CSV is empty: " + csvPath);
            return issues;
        }

        Map<String, Integer> idx = headerIndex(lines.get(0), "first_id", "second_id", "type");
        int firstIdx = idx.get("first_id"), secondIdx = idx.get("second_id"), typeIdx = idx.get("type");
        Set<String> validTypes = Set.of(
                "NOT_TOGETHER_SAME_SERVICE_HARD",
                "PREFER_TOGETHER_SAME_SERVICE_SOFT"
        );

        int row = 1;
        for (String line : lines.subList(1, lines.size())) {
            row++;
            if (line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            if (cols.length <= Math.max(Math.max(firstIdx, secondIdx), typeIdx)) {
                issues.add("Row " + row + ": too few columns.");
                continue;
            }
            String a = cols[firstIdx].trim();
            String b = cols[secondIdx].trim();
            String t = cols[typeIdx].trim();

            if (a.isEmpty()) issues.add("Row " + row + ": missing first_id.");
            if (b.isEmpty()) issues.add("Row " + row + ": missing second_id.");
            if (t.isEmpty()) issues.add("Row " + row + ": missing type.");

            if (!knownIds.contains(a)) issues.add("Row " + row + ": unknown first_id \"" + a + "\".");
            if (!knownIds.contains(b)) issues.add("Row " + row + ": unknown second_id \"" + b + "\".");
            if (a.equals(b)) issues.add("Row " + row + ": first_id equals second_id → \"" + a + "\".");
            if (!validTypes.contains(t)) issues.add("Row " + row + ": invalid type \"" + t + "\".");
        }
        return issues;
    }

    public static List<PairPreference> loadPairPreferencesCsv(Path csvPath, Map<String, Musician> byId) throws IOException {
        List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        if (lines.isEmpty()) return List.of();

        Map<String, Integer> idx = headerIndex(lines.get(0), "first_id", "second_id", "type");
        int firstIdx = idx.get("first_id"), secondIdx = idx.get("second_id"), typeIdx = idx.get("type");

        List<PairPreference> out = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            String aId = cols[firstIdx].trim();
            String bId = cols[secondIdx].trim();
            String typeStr = cols[typeIdx].trim();

            Musician a = byId.get(aId);
            Musician b = byId.get(bId);
            if (a == null || b == null) {
                throw new IllegalArgumentException("Unknown musician id in pairs CSV: " + aId + " / " + bId);
            }
            PairPreferenceType type = PairPreferenceType.valueOf(typeStr);
            out.add(new PairPreference(a, b, type));
        }
        return out;
    }

    // ---------- Helpers ----------

    private static Map<String, Integer> headerIndex(String headerLine, String... expectedColumns) {
        String[] hdr = headerLine.split(",", -1);
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < hdr.length; i++) {
            idx.put(hdr[i].trim().toLowerCase(Locale.ROOT), i);
        }
        for (String col : expectedColumns) {
            if (!idx.containsKey(col)) {
                throw new IllegalArgumentException("CSV header must contain: " + String.join(",", expectedColumns)
                        + ". Got: " + headerLine);
            }
        }
        return idx;
    }
}
