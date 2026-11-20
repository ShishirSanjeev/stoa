# Stoa

A personal Android home screen built on top of an Obsidian vault.

Everything Stoa shows comes from the vault's markdown files, and everything it changes is written back in the
formats Obsidian and the Serendipity dashboard already use, so the phone, Obsidian and the dashboard always agree.

## What it does

- **Home** — clock, level & XP, today's three, screen time, tasks due, a Serendipity web clipping, quick capture, a 4-app dock.
  Swipe up for a black-and-white **app drawer** (all apps + Stoa's sections + note search). Long-press an app to dock, hide or pause it.
- **Today** — today's three, morning/evening routines, habits (with streaks), projects (`#project`, ranked by `priority_rank`);
  **Reading** timer that logs to the book's note; **Stillness** timer with interval bells.
- **Notes** — **Writing** (write, then upload as `HOTA - <title>` linked into the book note), **Book tasks**, **Ideas**
  (→ Quick Capture folder), **Canvas** (JSON Canvas: text cards + images), **Screenshots** from the phone.
- **Gym** — offline workout log with rest timer, templates and history; each workout is written into the daily note.
- **Mindful pause** — a breathing screen before chosen apps, however they're opened (Accessibility service).

## Where things are written

| Action | File | Format |
| --- | --- | --- |
| Habit tick | today's daily note | `## Habits` / `- [x] Gym` |
| Routine tick | today's daily note | properties `morning_routine`, `evening_routine`, `routines_complete` |
| Today's three | today's daily note | `## Today's three` tasks |
| Task tick | the task's note | `- [x] … ✅ 2026-11-20` |
| Reading | book note + daily note | `## Reading log`, property `reading_minutes` |
| Stillness | daily note | property `meditation_minutes` |
| Workout | daily note | `## Workout` section + `Workout` property |
| Writing upload | `0001 Resources/HOTA - <title>.md` | linked from the book note |
| Idea | `9999 Quick Capture/<yyyy.MM.dd HH_mm_ss>.md` | |
| Habits, routines, achievements | `Dashboard State.md` | shared with the dashboard |
| Books, gym templates | `Stoa State.md` | |

XP and levels use the dashboard's own rules (ported line for line).

## Building

Every push to `main` builds a signed APK with GitHub Actions and publishes it as the **latest** release.
`app/stoa.jks` is a sideloading key so updates install over each other — keep the repository private.
