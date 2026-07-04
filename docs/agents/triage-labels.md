# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual tag strings used in this repo's issue tracker (`Tags:` front matter field in entries under `projects/menu-planner/tasks` in `cstdev/second-brain` — see `issue-tracker.md`).

`Status:` is reserved for actual progress (`Not Started` / `In Progress` / `Done`) and never holds a triage role — triage state lives only in `Tags:`.

| Label in mattpocock/skills | Tag string in our tracker | Meaning                                  |
| --------------------------- | ----------------------------- | ----------------------------------------- |
| `needs-triage`               | `needs-triage`                 | Maintainer needs to evaluate this issue   |
| `needs-info`                 | `needs-info`                   | Waiting on reporter for more information  |
| `ready-for-agent`            | `ready-for-agent`              | Fully specified, ready for an AFK agent   |
| `ready-for-human`            | `ready-for-human`              | Requires human implementation             |
| `wontfix`                    | `wontfix`                       | Will not be actioned                      |

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), add the corresponding string from this table to the entry's `Tags:` list (removing any other triage tag it previously had).
