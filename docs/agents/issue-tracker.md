# Issue tracker: Markdown in a separate repo

Issues and PRDs for this repo live as markdown notes in a separate GitHub repo: [`cstdev/second-brain`](https://github.com/cstdev/second-brain), under `projects/menu-planner`.

## Conventions

- This repo (`meal-planner-v2`) does not host its own issues — all tracking lives in `cstdev/second-brain`.
- Issues are entries under `projects/menu-planner/tasks` each task is an individual file
- Tasks should be linked to the menu-planner.base using the existing format in there
- Triage state is recorded as a `Status:` field on each entry (see `triage-labels.md` for the role strings).
- Comments and conversation history append under the relevant entry.

## When a skill says "publish to the issue tracker"

Clone or assume local access to `cstdev/second-brain`, then add a new entry under `projects/menu-planner/tasks`. If the repo isn't checked out locally, ask the user for its local path or offer to clone it.

## When a skill says "fetch the relevant ticket"

Read the relevant entry from `projects/menu-planner/tasks/` in `cstdev/second-brain`. The user will normally point to the entry directly.

## PRs as a triage surface

Not applicable — this tracker has no PR-based request surface.
