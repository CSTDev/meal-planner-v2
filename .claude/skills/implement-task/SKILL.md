---
name: implement-task
description: >
  Full build → review → merge lifecycle for a menu-planner task tracked in second-brain.
  Use when the user says "implement <task>.md", "ship <task>.md", or "work on <task>.md".
argument-hint: "<task-filename.md>"
allowed-tools: Bash, Read, Write, Edit, Glob, Grep, Agent, AskUserQuestion, mcp__github__create_pull_request, mcp__github__merge_pull_request, mcp__github__pull_request_read, mcp__github__subscribe_pr_activity, mcp__github__unsubscribe_pr_activity, mcp__Claude_Code_Remote__send_later, mcp__Claude_Code_Remote__delete_trigger
---

# Implement Task

Full build → review → merge lifecycle for a menu-planner task tracked in second-brain.

**Usage:** `/implement-task <task-filename.md>`

The task file lives in `second-brain/projects/menu-planner/tasks/`. The code repo is `meal-planner-v2`.

---

## Commit rules

- **Never include a Claude/AI signature or co-author line in any commit message.** Write plain, descriptive messages only.

---

## Step 1 — Pull latest

Rebase both repos onto their latest `main` before touching anything:

```bash
cd /path/to/meal-planner-v2 && git fetch origin && git rebase origin/main
cd /path/to/second-brain   && git fetch origin && git rebase origin/main
```

---

## Step 2 — Read the task

Read `second-brain/projects/menu-planner/tasks/<task-filename>` in full before implementing anything.

---

## Step 3 — Mark In Progress (direct to main)

Update `Status: In Progress` in the task file, then commit **directly to `main`** in second-brain — not to the feature branch:

```bash
cd /path/to/second-brain
git checkout main
git pull origin main
# edit the task file
git add projects/menu-planner/tasks/<task-filename>
git commit -m "task(menu-planner): mark <task-name> as In Progress"
git push origin main
git checkout claude/<branch-name>  # return to feature branch
```

---

## Step 4 — Build and review loop

Run `/build-and-review` with the task description as input (up to 3 implementer → reviewer iterations).

- All code changes go on the `meal-planner-v2` feature branch.
- Progress notes added to the task body during the loop go on the **second-brain feature branch** (not main).

---

## Step 5 — After the loop completes: triage tasks

If the reviewer's final pass left any **minor, non-critical comments unresolved** (warnings or suggestions the loop didn't address), create a task file for each one in `second-brain/projects/menu-planner/tasks/` with:

```yaml
Status: Not Started
Tags:
  - needs-triage
```

Commit these to the **second-brain feature branch**. Skip this step if all comments were resolved.

---

## Step 6 — Create the PR

Create a PR in `meal-planner-v2` targeting `main`. No PR template exists — write a plain summary. Include a "Not fixed" section listing any triage tasks created in Step 5.

---

## Step 7 — Watch CI

Subscribe to PR activity and schedule a 60-minute fallback check-in (`send_later`):

```
Fallback check-in for meal-planner-v2 PR #N. Re-check CI and mergeability. If green, merge and mark task Done. Re-arm if still waiting.
```

Wait for all check runs to reach `success`:
- On failure: read the job logs, fix, push, re-wait.
- Confirm that any new service (e.g. Python scraper tests) is actually exercised by CI before treating the run as representative.

---

## Step 8 — Merge

Once all checks are green, merge (squash) the PR. Cancel the fallback timer. Unsubscribe from PR activity.

---

## Step 9 — Mark Done (direct to main)

Update `Status: Done` in the task file, then commit **directly to `main`** in second-brain:

```bash
cd /path/to/second-brain
git checkout main
git pull origin main
# edit the task file
git add projects/menu-planner/tasks/<task-filename>
git commit -m "task(menu-planner): mark <task-name> as Done — PR #N merged"
git push origin main
git checkout claude/<branch-name>
```

---

## Step 10 — Second-brain PR

Create a PR in `second-brain` for the feature branch. This covers: progress notes added during the loop, and any triage task files from Step 5.
