## Scope & priorities

The purpose of this app is menu planning and aiding with the shopping list / shopping — **not** to be a UI to cook from and follow recipes. Most recipes are stored elsewhere in hardcopy. Treat recipe-reading/detail features (e.g. the recipe detail page) as low priority relative to planning and shopping-list work.

## Agent skills

### Issue tracker

Issues and PRDs are tracked as markdown entries in a separate repo, `cstdev/second-brain`, under `Projects/Menu Planner.md`. No PR-based triage surface. See `docs/agents/issue-tracker.md`.

### Triage labels

Default role names used as-is (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`), recorded via a `Status:` field on each entry. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` + `docs/adr/` at the repo root, shared across all services under `services/`. See `docs/agents/domain.md`.
