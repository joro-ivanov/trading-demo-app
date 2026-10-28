---
name: it-gen
description: Generate, review and score backend integration tests (Testcontainers + WireMock) for only the services/endpoints changed on this branch, looping writer → gates → reviewer until score ≥ 9/10.
argument-hint: "[--base <ref>] [--all] [<service> [\"<METHOD> <path>\"]]"
disable-model-invocation: true
---

# /it-gen: integration-test generation loop

You are the **orchestrator**. You run the deterministic steps yourself and delegate the thinking to
three subagents: `it-sme` (case design + coverage review), `it-writer` (implementation) and
`it-reviewer` (scoring). Never write test code yourself. Never commit.

Arguments: `$ARGUMENTS`
- empty → changes since `merge-base(HEAD, origin/main)` + uncommitted + untracked
- `--base <ref>` → changes since `<ref>`
- `--all` → every service, full generation
- `<service>` (e.g. `order` or `order-service`) → that service, full generation
- `<service> "<METHOD> <path>"` → that service, only that endpoint (pass the filter to it-sme)

Shared references: [conventions.md](conventions.md) · [case-catalog.md](case-catalog.md) · [rubric.md](rubric.md)

## 0. Preflight (stop on failure; never install anything)

Run `mvn -v` and `docker info --format '{{.ServerVersion}}'`.
- Maven must report **Java 25** (`<java.version>25` in the root `pom.xml`).
- Docker must answer (Testcontainers needs it).
If either fails, stop and tell the user exactly what is missing (e.g. "JDK 25 not installed /
JAVA_HOME not set", "Docker daemon not running"). Ask them to fix it. Do not install anything
and do not change the build to work around it.

## 1. Scope (deterministic)

```bash
java .claude/skills/it-gen/scripts/Scope.java <args without the endpoint filter>
```
- `nothingToDo: true` → report "No backend main-code changes; no integration tests to update",
  list `ignoredFiles` briefly, and stop.
- Group the work **by service**: each entry in `services`, plus the consumer services named in
  `consumerStubChecks` / `eventContractChecks` (stub/contract-only scope for those).
- Show the user a short scope table: service, why it is in scope, changed controllers/listeners, and
  checks. Then continue without waiting.

Snapshot the working tree **before any agent runs**, so the scope gate can tell the user's own
changes apart from the writer's:
```bash
git status --porcelain=v1 -uall > "$SCRATCH/it-gen-before.txt"
git status --porcelain=v1 -uall | awk '{print $NF}' | xargs -I{} sh -c 'test -f "{}" && git hash-object "{}" | sed "s|^|{} |"' > "$SCRATCH/it-gen-before-hashes.txt"
```
(`$SCRATCH` = your scratchpad directory.)

## 2. Plan: it-sme `plan` mode (one per service, in parallel)

Spawn one `it-sme` agent per service, all in **one message**. Prompt: `Mode: plan`, the service's scope
JSON, the endpoint filter if any, and the relevant stub/contract checks. Keep each returned
matrix. Print a per-service summary: in-scope endpoints → test classes, case counts per category,
`n/a` categories, and suspected product bugs.

## 3. Write: it-writer (one per service, at most 2 at a time)

Testcontainers is heavy, so run at most 2 writers concurrently. Prompt: the service scope JSON + its
full matrix. Keep each writer's agent id. Fix rounds go back to the **same** writer with
`SendMessage`, so it keeps its context (load it with `ToolSearch select:SendMessage` if it is not
loaded). If SendMessage is unavailable, spawn a fresh `it-writer` with the matrix + findings.

## 4. Gates (you run these yourself; they are deterministic)

For each service, after its writer reports:

1. **Scope check**: compare `git status --porcelain=v1 -uall` and file hashes with the snapshot.
   Files that are new or changed since the snapshot may only be
   `services/<svc>/src/test/java/**/it/**` for in-scope classes/harness, and `services/<svc>/pom.xml` only for a new service
   (the test-tooling blocks from conventions.md, so check its diff). Any other new change is a **violation**. Send it back to the
   writer to undo. Never revert files yourself, and never touch the user's own pre-existing changes.
2. **Rerun 3×** (determinism):
   ```bash
   mvn -pl services/<svc> verify -Dit.test='<in-scope IT classes>' -Dfailsafe.failIfNoSpecifiedTests=false
   ```
   Run it three times sequentially. Record pass/fail per run and the failing tests (from
   `target/failsafe-reports/`). This also runs PMD, Checkstyle and ArchUnit.
3. **No-logic lint**:
   ```bash
   java .claude/skills/it-gen/scripts/TestLint.java <each in-scope IT class path>
   ```

## 5. Review: it-reviewer + it-sme `coverage` (in parallel, one message)

- `it-reviewer`: in-scope test/harness paths, the matrix, and the gate results (scope violations,
  3 rerun results, build/PMD/Checkstyle/ArchUnit status, TestLint output). Do **not** pass the
  writer's report or reasoning. The review must be independent.
- `it-sme` `Mode: coverage`: the matrix, the changed test files, and the reviewer JSON from the
  previous round (if any).

## 6. Loop

A service is **done** when: score ≥ 9.0, `blocking` is empty, `missingMust` is empty, the gates are
clean, and all 3 reruns passed.

Otherwise send the writer one consolidated fix list: blocking issues → missingMust/newCases (must)
→ weaklyCovered → improvements. Then go back to step 4. **At most 4 rounds per service.** After
round 4, stop that service and report what is still open.

Print one progress line per round: `order-service round 2: 8.1 → 9.3, blocking 0, missing must 0`.

## 7. Final report

Per service: in-scope endpoints, files changed, cases added/updated/removed, score history, rerun results, `@Disabled("BUG: …")` tests with evidence (**product
bugs need a human decision**), and open items if the round limit was hit. End with the exact
`mvn` command to rerun the new tests. Do not commit. The user reviews the diff.
