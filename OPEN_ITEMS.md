## Excluded musicians crash the solve if they have active pair preferences

`RosterService.loadPairPreferences()` throws `IllegalStateException`
if a pair preference references a musician id that isn't in the
(now-excluded-filtered) musicians list — meaning excluding a musician
who's part of an active "prefer together" or "must not serve
together" pairing will crash the entire solve, not just silently drop
that preference.

**Needs a decision before fixing:**
- Silently skip pair preferences where either musician is excluded
  (simplest, but could hide a preference the admin forgot about)
- Warn in the API response but continue solving without that
  preference
- Block the exclude action itself (via the API) while the musician has
  active pair preferences, forcing the admin to remove/reassign the
  preference first

Reproduce: create a pair preference between two musicians, exclude
one of them, then run a solve — it will fail instead of completing.