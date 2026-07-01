# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual status strings used in this repo's issue tracker (`Status:` field in entries under `Projects/Menu Planner.md` in `cstdev/second-brain` — see `issue-tracker.md`).

| Label in mattpocock/skills | Status string in our tracker | Meaning                                  |
| --------------------------- | ----------------------------- | ----------------------------------------- |
| `needs-triage`               | `needs-triage`                 | Maintainer needs to evaluate this issue   |
| `needs-info`                 | `needs-info`                   | Waiting on reporter for more information  |
| `ready-for-agent`            | `ready-for-agent`              | Fully specified, ready for an AFK agent   |
| `ready-for-human`            | `ready-for-human`              | Requires human implementation             |
| `wontfix`                    | `wontfix`                       | Will not be actioned                      |

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), set the entry's `Status:` field to the corresponding string from this table.
