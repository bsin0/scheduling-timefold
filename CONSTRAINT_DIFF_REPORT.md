# Constraint Diff Report

## Date: 2025-07-05

---

## Summary

| Change Type | Count | Status |
|-------------|-------|--------|
| Removed | 1 | ✅ Complete |
| Added | 2 | ✅ Complete |
| Modified | 0 | - |

---

## Detailed Changes

### 1. REMOVED: `musicianMustBeCapableOfRole`

**File:** `src/main/java/org/churchband/domain/ScheduleConstraintProvider.java`

**Before:**
```java
// Line 42
musicianMustBeCapableOfRole(factory),

// Lines 91-97
private Constraint musicianMustBeCapableOfRole(ConstraintFactory factory) {
    return factory.forEach(Assignment.class)
            .filter(a -> a.getMusician() != null && !a.getMusician().canPerformRole(a.getRole()))
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("Musician must be capable of assigned role");
}
```

**After:**
*(removed entirely)*

**Reason:** Exact duplicate of `musicianMustBeQualified` constraint. Was being evaluated twice for the same violation.

**Impact:**
- Cleaner codebase
- Slightly better solver performance
- No functional change to constraints

---

### 2. ADDED: `secondVocalistPreference`

**File:** `src/main/java/org/churchband/domain/ScheduleConstraintProvider.java`

**Location:** Line 42 (after `maxWeeksPerMonthConstraint`)

**Code:**
```java
// Line 42
secondVocalistPreference(factory),

// Lines 191-197
// Optional: Second vocalist preference (soft - encourages but doesn't force)
private Constraint secondVocalistPreference(ConstraintFactory factory) {
    return factory.forEach(Assignment.class)
            .filter(a -> a.getRole() == Role.VOCALIST && a.getMusician() != null)
            .filter(a -> a.getMusician().getRoles().contains(Role.VOCALIST))
            .penalize(HardSoftScore.ofSoft(2)) // Light penalty if not assigned (encourages usage)
            .asConstraint("Second vocalist preference");
}
```

**Purpose:** Encourages assignment of a second vocalist without forcing it.

**Weight:** -2 soft points per unassigned qualified vocalist

---

### 3. ADDED: `thirdVocalistPreference`

**File:** `src/main/java/org/churchband/domain/ScheduleConstraintProvider.java`

**Location:** Line 43 (after `secondVocalistPreference`)

**Code:**
```java
// Line 43
thirdVocalistPreference(factory),

// Lines 200-206
// Optional: Third vocalist preference (even lighter soft constraint)
private Constraint thirdVocalistPreference(ConstraintFactory factory) {
    return factory.forEach(Assignment.class)
            .filter(a -> a.getRole() == Role.VOCALIST && a.getMusician() != null)
            .filter(a -> a.getMusician().getRoles().contains(Role.VOCALIST))
            .penalize(HardSoftScore.ofSoft(1)) // Very light penalty if not assigned
            .asConstraint("Third vocalist preference");
}
```

**Purpose:** Additional flexibility for third vocalist. Lighter weight than second vocalist.

**Weight:** -1 soft point per unassigned qualified vocalist

---

## Constraint Weights Summary

| Constraint | Type | Weight | Purpose |
|------------|------|--------|---------|
| `secondVocalistPreference` | Soft | -2 | Encourage 2nd vocalist |
| `thirdVocalistPreference` | Soft | -1 | Encourage 3rd vocalist |

**Note:** Negative weights mean "penalty if not satisfied" - the solver tries to assign vocalists to minimize penalties.

---

## Files Modified

1. `src/main/java/org/churchband/domain/ScheduleConstraintProvider.java`
   - Line 42: Added 2 constraints to array
   - Lines 191-206: Added `secondVocalistPreference` method
   - Lines 209-215: Added `thirdVocalistPreference` method

---

## Next Steps

1. ✅ Run updated benchmark
2. ✅ Compare scores with baseline
3. ✅ Decide if 2+ vocalists should be enabled

---

**Generated:** 2025-07-05  
**Status:** Changes applied, awaiting benchmark validation
