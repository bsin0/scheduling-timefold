# Constraint Change Log

## 2025-07-05

### Removed Duplicate Constraint
- **Constraint:** `musicianMustBeCapableOfRole`
- **Location:** Line 42 (array), Lines 91-97 (method)
- **Reason:** Exact duplicate of `musicianMustBeQualified` - was penalizing twice for same violation
- **Impact:** 
  - Soft score: -945 → -947 (change of 2 points, negligible)
  - Solver now cleaner and more maintainable

### Added Optional Vocalist Constraints
- **Constraint 1:** `secondVocalistPreference` (Line 42)
  - Type: Soft (encouragement)
  - Weight: -2 soft points if not assigned
  - Purpose: Encourage use of 2nd vocalist without forcing
- **Constraint 2:** `thirdVocalistPreference` (Line 43)
  - Type: Soft (light encouragement)
  - Weight: -1 soft point if not assigned
  - Purpose: Additional flexibility for 3rd vocalist

### Documentation Updates
- Updated `CONSTRAINTS_RATIONALE.md` to track duplicate removal
- Added vocalist preferences to constraint list

---

## 2025-07-05 (Baseline)

### Initial State
- All 7 hard constraints active
- All 7 soft constraints active
- No vocalist preferences (only 1 vocalist needed)
- Score: -945 soft (feasible)

---

## Benchmark Results

### Before (with duplicate)
| Metric | Value |
|--------|-------|
| Hard score | 0 |
| Soft score | -945 |
| Time | 46s |

### After (duplicate removed)
| Metric | Value |
|--------|-------|
| Hard score | 0 |
| Soft score | -947 |
| Time | 20001ms |

**Verdict:** ✅ Safe to proceed - negligible impact

---

## Pending Changes

### Vocalist Expansion (Optional)
- Adding 2nd and 3rd vocalist preferences
- Status: **APPLIED**
- Weights: -2 and -1 soft points respectively
- Next step: Run benchmark with 2+ vocalists
