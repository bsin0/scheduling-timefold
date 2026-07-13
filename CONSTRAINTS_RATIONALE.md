# Constraint Rationale Documentation

**Project:** Church Band Scheduler (Timefold Solver)  
**Purpose:** Explain the reasoning behind each constraint so future agents can understand the design decisions and make informed modifications.

---

## Overview

This scheduler optimizes volunteer assignments for church Sunday services using Timefold's constraint-based optimization. The system balances:
- **Hard constraints** (must never be violated)
- **Soft constraints** (optimize schedule quality)

---

## Hard Constraints

Hard constraints are non-negotiable. The solver will produce an invalid solution if any are violated.

### 1. `musicianMustBeAvailable`
- **Rule:** A musician can only be assigned to dates they've marked as available
- **Rationale:** Respects volunteer commitments. Someone unavailable on a Sunday shouldn't be scheduled.
- **Location:** Line 48-52

### 2. `musicianMustBeQualified`
- **Rule:** A musician can only be assigned roles they're qualified for
- **Rationale:** Safety and competence. Someone can't play drums if they've never held drumsticks.
- **Location:** Line 54-58

### 3. `avoidSameServiceDoubleBooking`
- **Rule:** A musician cannot be assigned to the same service twice (unless allowed multi-role)
- **Rationale:** Prevents logistical chaos. One person can't play guitar AND keyboard at the same time (except pre-approved combos).
- **Location:** Line 69-77

### 4. `bandDirectorCannotBeSolo`
- **Rule:** Band Director must have at least one additional role in the same service
- **Rationale:** The director leads the band, doesn't just stand at the front alone. They need to play/lead something.
- **Location:** Line 60-67

### 5. `couplesWithKidsCannotServeSameService`
- **Rule:** Couples with dependent children cannot serve the same Sunday
- **Rationale:** Childcare logistics. If both parents work the same service, no one watches the kids.
- **Location:** Line 80-89

### 6. `musicianMustBeCapableOfRole`
- **Rule:** Redundant check ensuring musician can perform assigned role
- **Rationale:** Defense-in-depth. Double-checks qualification logic.
- **Location:** Line 92-99

### 7. `maxWeeksPerMonthConstraint`
- **Rule:** Each musician has a maximum weeks per month they can serve (configurable per person)
- **Rationale:** Prevents burnout. Some musicians have family/work commitments limiting their availability windows.
- **Location:** Line 101-114
- **Config:** Set in musicians.csv via `max_weeks_per_month` field

---

## Soft Constraints

Soft constraints optimize schedule quality. The solver tries to minimize penalties, but some trade-offs are expected.

### 1. `avoidOverbooking`
- **Rule:** Penalize musicians with more than 4 total assignments across the horizon
- **Rationale:** Balance volunteer load. No one should carry the entire schedule. The "4" is calibrated to the ~8-Sunday horizon.
- **Location:** Line 118-123
- **Penalty:** +1 soft point for each assignment beyond 4

### 2. `incrementalDiversityPenalty`
- **Rule:** Penalize assigning a musician who's already been used many times
- **Rationale:** Encourage diversity. Don't keep pulling the same 2-3 volunteers; spread the work.
- **Location:** Line 126-135
- **Penalty:** +3 soft points per additional assignment (increases with each use)

### 3. `balanceWorkload`
- **Rule:** Penalize deviation from an ideal of 4 assignments per musician
- **Rationale:** Fair distribution. Some serve more than others; this minimizes inequality.
- **Location:** Line 138-145
- **Penalty:** +2 soft points × |actual - ideal|
- **Ideal:** 4 assignments (matches horizon of ~8 Sundays)

### 4. `rewardAllowedMultiRole`
- **Rule:** Reward when allowed multi-role combos are used (WL+Guitar, BD+Keys, etc.)
- **Rationale:** Efficiency. Some musicians can legitimately play multiple roles; encourage this where safe.
- **Location:** Line 147-155
- **Reward:** -1 soft point (reduces penalty score)
- **Allowed combos:**
  - Worship Leader + Guitarist
  - Band Director + Guitarist
  - Band Director + Keyboardist
  - Band Director + Bassist

### 5. `couplesPreferTogetherSoft`
- **Rule:** Penalize couples who don't serve together (when they've marked a preference)
- **Rationale:** Some couples enjoy serving as a unit. Optional preference, not required.
- **Location:** Line 158-166
- **Penalty:** +2 soft points when separated

### 6. `couplesPreferTogetherStrong`
- **Rule:** Stronger penalty for specific couples (one-car households)
- **Rationale:** These couples literally can't make it to separate services due to childcare logistics.
- **Location:** Line 169-177
- **Penalty:** +10 soft points (5× stronger than regular couples)
- **Couples:** ernest_l/sarah_l, jon_w/shanti_s, phebe_r/chris_l, anjita_s/saroj_n, miranda_s/klaytin_s, annie_l/david_l

### 7. `penalizeConsecutiveServices`
- **Rule:** Penalize musicians serving consecutive weeks (Sundays in a row)
- **Rationale:** Rest is important. Prevents burnout from consecutive service commitments.
- **Location:** Line 180-191
- **Penalty:** +3 soft points for consecutive weekly assignments

---

## Multi-Role Allowed Combinations

Pre-approved combinations where one person can legitimately play multiple roles simultaneously:

| Combo | Rationale |
|-------|-----------|
| Worship Leader + Guitarist | WL often accompanies guitar; complementary roles |
| Band Director + Guitarist | BD plays rhythm guitar while directing |
| Band Director + Keyboardist | BD plays keys while directing |
| Band Director + Bassist | BD plays bass while directing |

**Location:** Lines 15-25 in `ScheduleConstraintProvider.java`

---

## Configuration Notes

### Key Tuning Parameters

| Parameter | Current Value | Location | What It Controls |
|-----------|---------------|----------|------------------|
| Ideal assignments per musician | 4 | Line 142 | Balance workload target |
| Overbooking threshold | 4 | Line 121 | When to penalize overuse |
| Overbooking penalty | +1 per extra | Line 122 | Cost of exceeding threshold |
| Consecutive week penalty | +3 | Line 190 | Cost of back-to-back services |
| Diversity penalty | +3 per use | Line 134 | Cost of reusing popular musicians |
| Couples-prefer-together | +2 | Line 165 | Cost of separating couples |
| Strong couples penalty | +10 | Line 176 | Cost for one-car households |

### Calibration Notes

- The "4" assignments ideal is calibrated to an 8-Sunday horizon
- Horizon: 2025-11-09 to 2025-12-28 (8 Sundays)
- Termination: 10 seconds (App.java)
- Environment mode: FULL_ASSERT (development)

---

## Adding New Constraints

When adding constraints, consider:

1. **Hard vs Soft:** Should this be mandatory or optional?
2. **Penalty strength:** Use `HardSoftScore.ofSoft(N)` where N reflects priority
3. **Trade-offs:** Higher penalty = solver prioritizes this more
4. **Configurability:** Can users adjust this per-musician?

---

## Benchmarking Strategy

**Purpose:** Measure constraint changes without breaking existing functionality.

**Process:**
1. Save current scores to `benchmark_baseline.json`
2. Make constraint changes
3. Run new benchmark
4. **Only proceed if scores match or improve** (no degradation in hard constraints)
5. Review score explanations to understand trade-offs

**Location:** Run via `mvn exec:java` (see App.java)

---

## Version History

| Date | Change | Rationale |
|------|--------|-----------|
| 2025-07-05 | Initial constraint documentation | Establish baseline understanding |

---

**Last Updated:** 2025-07-05  
**Maintainer:** Future agents should update this file when modifying constraints
