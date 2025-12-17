# Church Band Scheduler

A Java/Maven project that uses **Timefold Solver** (the successor to OptaPlanner) to auto‑schedule musicians and tech volunteers for church Sunday services. It reads **CSV inputs**, enforces **hard constraints** (availability, qualifications, no double‑booking) and optimizes **soft preferences** (pairing, fairness, diversity), producing a **chronological schedule** and a **per‑musician summary**.

> Timefold is the open‑source planning solver that evolved from OptaPlanner; it uses constraint streams and a multi‑level score (hard/soft) to evaluate schedules efficiently.  
> *You don’t need OptaPlanner installed; this project uses Timefold Core.* 

---

## ✨ Features

- **CSV-based input**
  - `musicians.csv`: id, name, role capabilities, available dates
  - `pairs.csv`: pair preferences (hard “not together” and soft “prefer together”)
- **Roles & multi‑role support**
  - Band roles: `BAND_DIRECTOR, WORSHIP_LEADER, VOCALIST, GUITARIST, BASSIST, KEYBOARDIST, DRUMMER, SOUND, LYRICS, CAMERA`
  - Specific multi‑role combos allowed (e.g., Worship Leader + Guitarist, Band Director + Keyboardist)
- **Pair preferences**
  - **Hard**: must **not** serve in the same service  
  - **Soft**: **prefer** serving together
- **Constraints**
  - Hard constraints: availability, qualifications, no illegal double‑booking, band director not solo
  - Soft constraints: avoid overbooking, balance workload, encourage diversity, reward allowed multi‑role, prefer pairs together
- **Schedule optimization**
  - 8 Sundays from **2025‑11‑09** to **2025‑12‑28**
  - Prints a chronological schedule plus a per‑musician summary
  - Includes a detailed score explanation (all matches, hard‑only, per‑assignment)


---

## Prerequisites

To run this project, you need:

- **Java JDK 17 or higher**  
  [Download](https://adoptium.net/) and install for your OS.
- **Maven** (for building and running)  
  [Install instructions](https://maven.apache.org/install.html)
- **IDE (optional but recommended)**  
  Examples:
  - [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/)
  - [Eclipse IDE](https://www.eclipse.org/downloads/)

---

## Project Structure
```
scheduling/
├─ pom.xml
├─ README.md
├─ config/
│  ├─ musicians.csv
│  └─ pairs.csv
└─ src/
   └─ main/
      ├─ java/
      │  └─ org/churchband/
      │     ├─ Main.java
      │     ├─ App.java
      │     ├─ util/
      │     │  ├─ RosterCsv.java
      │     │  └─ RosterIndex.java
      │     └─ domain/
      │        ├─ Assignment.java
      │        ├─ Musician.java
      │        ├─ PairPreference.java
      │        ├─ PairPreferenceType.java
      │        ├─ Role.java
      │        ├─ Schedule.java
      │        ├─ ScheduleConstraintProvider.java
      │        └─ SundayService.java
      └─ resources/
         └─ (none – solver configured programmatically)
```

## 🧠 Constraints (Hard vs Soft)
This project uses Constraint Streams in ScheduleConstraintProvider.java to compute a HardSoftScore:
### Hard constraints (must not break)

Unavailable musician assigned
A musician can only be scheduled on dates listed in their availability.
Unqualified musician assigned
Assign only roles each musician can perform.
Musician double‑booked in same service (unless allowed multi‑role)
Prevent same‑service collisions except specific allowed multi‑role combos.
Band Director cannot be solo
If someone is Band Director, they must have at least one other role the same service.
Couples (kids): partners cannot both serve same service
(From PairPreferenceType.NOT_TOGETHER_SAME_SERVICE_HARD)

### Soft constraints (optimize quality)

Musician overbooked
Penalize >4 total assignments in the horizon (adjust the 4 as needed).
Incremental diversity: penalize assigning already‑overused musician
The more often a musician is used, the higher the soft penalty for additional assignments.
Balance workload across musicians
Penalize deviation from an ideal target (e.g., 4).
Allowed multi‑role usage rewarded
Soft reward for permitted same‑service multi‑role combos (e.g., WL+Guitar).
Couples prefer serving together (penalize when alone)
(From PairPreferenceType.PREFER_TOGETHER_SAME_SERVICE_SOFT)

## 📊 Example Output (illustrative)

The following is a realistic synthetic example based on your CSVs and constraints.
Your actual output will differ depending on solver randomness, termination time, and constraint weights.

### 1) Chronological schedule
```
Date         Role            Musician
------------------------------------------------------------
2025-11-09   BAND_DIRECTOR   Jon R
2025-11-09   WORSHIP_LEADER  Bryan S
2025-11-09   VOCALIST        Joel O
2025-11-09   VOCALIST        Sarah L
2025-11-09   GUITARIST       Jon R
2025-11-09   BASSIST         Ernest L
2025-11-09   KEYBOARDIST     Ashley S
2025-11-09   DRUMMER         Saroj
2025-11-09   SOUND           Gene K
2025-11-09   LYRICS          Chris
2025-11-09   CAMERA          Klaytin

2025-11-16   BAND_DIRECTOR   Ashley S
2025-11-16   WORSHIP_LEADER  Jon R
2025-11-16   VOCALIST        Phebe R
2025-11-16   VOCALIST        Mariah G
2025-11-16   GUITARIST       Bryan S
2025-11-16   BASSIST         Shane C
2025-11-16   KEYBOARDIST     Shanti S
2025-11-16   DRUMMER         Levi T
2025-11-16   SOUND           Miranda
2025-11-16   LYRICS          Jorg E
2025-11-16   CAMERA          Philip L

... (services 2025‑11
```


### 2) Summary: roles served by each musician per date
```

Summary: Roles served by each musician per date
------------------------------------------------------------
Adrian          : 2 Sundays
  2025-11-23 -> [GUITARIST]
  2025-12-21 -> [GUITARIST]

Ashley S        : 3 Sundays
  2025-11-09 -> [KEYBOARDIST]
  2025-11-16 -> [BAND_DIRECTOR]
  2025-12-14 -> [KEYBOARDIST,WORSHIP_LEADER]

Bryan S         : 3 Sundays
  2025-11-16 -> [GUITARIST]
  2025-12-07 -> [WORSHIP_LEADER]
  2025-12-28 -> [GUITARIST]

Ernest L        : 2 Sundays
  2025-11-09 -> [BASSIST]
  2025-12-28 -> [BASSIST]

... (others)
```

## 🛠️ Configuration Notes

Horizon: 8 Sundays starting 2025‑11‑09 (App.java)
Termination: Duration.ofSeconds(10) — adjust as needed
Environment mode: FULL_ASSERT — helpful during development
Allowed multi‑role combos (see ScheduleConstraintProvider#ALLOWED_MULTI_ROLE_COMBINATIONS)

WL + Guitar
BD + Guitar
BD + Keys
BD + Bass

## 🔄 Extending the Scheduler
### Add/modify roles

### Edit Role.java (enum)
Update musicians.csv role lists
Consider whether new multi‑role combos should be allowed (add to ALLOWED_MULTI_ROLE_COMBINATIONS)

### Change constraint weights / logic

Open ScheduleConstraintProvider.java and adjust penalty/reward magnitudes (e.g., HardSoftScore.ONE_SOFT, HardSoftScore.ofSoft(5))
Add/remove filters or joins to refine the behavior

### Adjust planning horizon

In App.java, change the start date or number of weeks
For mid‑horizon updates, consider “pinning” assignments you don’t want to change between runs

CSV validation

RosterCsv and RosterIndex perform friendly checks and report issues before solving
Headers must match exactly:

musicians.csv: id,name,roles,available_dates (semicolon‑separated lists)
pairs.csv: first_id,second_id,type (types restricted to enum values)

## 📄 License
This project is licensed under the MIT License.

MIT License

Copyright (c) 2025 Church Band Scheduling contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the “Software”), to deal
in the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included
in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## 🧩 Appendix: Example CSVs (as used in this project)
config/musicians.csv (headers must be exact)
```

id,name,roles,available_dates
jon_r,Jon R,KEYBOARDIST;WORSHIP_LEADER;GUITARIST;BAND_DIRECTOR,2025-11-09;2025-11-16;2025-11-30;2025-12-07;2025-12-14;2025-12-21;2025-12-28
ashley_s,Ashley S,KEYBOARDIST;WORSHIP_LEADER;BAND_DIRECTOR;LYRICS,2025-11-09;2025-11-16;2025-11-23;2025-11-30;2025-12-07;2025-12-21;2025-12-28
...
```
config/pairs.csv
```

first_id,second_id,type
joel_o,mariah_g,NOT_TOGETHER_SAME_SERVICE_HARD
ernest_l,sarah_l,PREFER_TOGETHER_SAME_SERVICE_SOFT
...
```
