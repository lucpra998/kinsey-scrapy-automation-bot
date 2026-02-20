# CI/CD Troubleshooting Guide

## GitHub Actions Timeout Limits

### The 6-Hour Hard Cap

GitHub-hosted runners (`ubuntu-latest`, `windows-latest`, `macos-latest`) enforce a
**maximum job execution time of 6 hours (360 minutes)**, regardless of the
`timeout-minutes` setting in the workflow YAML.

```
GitHub hard cap (hosted runners): 6 hours = 360 minutes
```

Setting `timeout-minutes` to a value higher than `360` (for example `1440` for
24 hours) has **no effect**: the runner will still be terminated at the 6-hour mark
by GitHub's infrastructure. The job will appear in the Actions UI as having timed out.

### How `timeout-minutes` Works

`timeout-minutes` can be set at two levels:

| Level | Example | Effect |
|-------|---------|--------|
| **Job** | `jobs.test.timeout-minutes: 350` | The entire job (all steps combined) is cancelled after 350 min |
| **Step** | `steps[n].timeout-minutes: 10` | Only that individual step is cancelled after 10 min |

If only a job-level timeout is set and an individual step hangs indefinitely,
the job-level timer still fires eventually. Per-step timeouts provide faster,
more granular feedback.

### Current Workflow Configuration

Both `ci-cd-testng-parallel.yml` (master) and `manual.yml` (development-shivang)
use these values:

| Setting | Value | Reason |
|---------|-------|--------|
| `jobs.test.timeout-minutes` | `350` | Fires before GitHub's 6-hour hard cap, leaving 10 min for post-job steps (cache save, artifact upload) |
| `Checkout` step timeout | `5 min` | Simple git clone; should complete in seconds |
| `Set up JDK 11` step timeout | `10 min` | Tool download/cache restore |
| `Restore UPC progress checkpoint` step timeout | `5 min` | Cache lookup |
| `Verify Chrome installation` step timeout | `5 min` | Version check only |
| `Build with Maven` step timeout | `10 min` | Incremental compile; first run may download dependencies |
| `Run TestNG Parallel Test Suite` step timeout | `320 min` | Bulk of the job; leaves headroom for setup/cleanup |
| `Upload TestNG Reports` step timeout | `10 min` | Artifact upload |
| `Upload Surefire Reports` step timeout | `10 min` | Artifact upload |

---

## Concurrency and Duplicate Runs

### Why Runs Were Being Cancelled

Without a `concurrency` block, every push to a branch starts a new workflow run
**independently**. Multiple runs can run simultaneously, and users were manually
cancelling older runs to reduce noise. This interrupted long-running scrapes that
had already processed hundreds of UPCs.

### Current Concurrency Configuration

Both workflows now include:

```yaml
concurrency:
  group: scrape-${{ github.ref }}
  cancel-in-progress: false
```

- `group: scrape-${{ github.ref }}` — all runs triggered by the same branch share
  one concurrency slot.
- `cancel-in-progress: false` — a new push **queues** the new run instead of
  cancelling the in-progress scrape. This is intentional: the scraper uses a
  resume/checkpoint mechanism (`ProgressTracker`), and killing a mid-run scrape
  wastes the work already done.

> **Note:** If you want each push to always run the latest code immediately
> (discarding any in-progress run), change `cancel-in-progress` to `true`.

---

## Resume / Checkpoint Mechanism

The scraper supports resuming from where it left off across multiple CI runs:

1. During a run, `ProgressTracker.markProcessed(upc)` writes each completed UPC
   to `ScrapingOutputResults/progress/`.
2. At the end of the job (or when the job times out), `actions/cache` saves that
   directory under the key `upc-progress-<ref>-<run_id>`.
3. On the next run, `actions/cache` restores the latest saved progress via
   `restore-keys: upc-progress-<ref>-`.
4. `UPCAddToCartParallelTest.batches()` calls `ProgressTracker.loadProcessed()`
   and removes already-processed UPCs from the queue.

This means you can simply **re-run** the workflow after a timeout, and it will
continue from where it stopped.

> **Important:** The cache is saved by the `Post Restore UPC progress checkpoint`
> step that runs during post-job cleanup. If the runner is killed **abruptly** by
> the hard 6-hour cap (rather than by the workflow's `timeout-minutes`), post-job
> steps may not complete. Setting `timeout-minutes: 350` ensures the workflow
> fires its own timeout at 350 min, which **does** trigger post-job cleanup steps.

---

## Heartbeat Logging

The `Run TestNG Parallel Test Suite` step starts a background heartbeat process
that prints a status line every 5 minutes:

```
[HEARTBEAT] 2026-02-20T20:00:00Z - scraping still in progress (3.1G used)
```

This serves two purposes:
1. **Visibility** — you can see how long the step has been running and approximately
   how much memory is in use.
2. **Idle detection** — GitHub Actions can mark a step as failed if it produces no
   output for a long time. The heartbeat prevents false idle timeouts.

---

## Diagnosing a Timeout

When a run times out, check the following in the Actions log:

1. **Which step timed out?** Look for `##[error]The operation was canceled.` or
   `Error: The operation was canceled.` under a specific step name.
2. **How far did the scraper get?** The last `STEP ->` or `[HEARTBEAT]` log line
   shows the last UPC being processed before the cancellation.
3. **Did the cache save?** Look for `Post Restore UPC progress checkpoint` in the
   post-job section. If it shows `Cache saved successfully`, the resume checkpoint
   is intact.
4. **Restart the job** using GitHub's "Re-run failed jobs" or "Re-run all jobs"
   button. The scraper will resume from the last checkpoint.

### Common Failure Modes

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| `session not created: Chrome instance exited` | Chrome version mismatch or insufficient memory | Check the `Verify Chrome installation` step output; re-run (transient) |
| `The operation was canceled.` after ~360 min | GitHub 6-hour hard cap hit | Re-run; progress is checkpointed |
| `The operation was canceled.` after ~350 min | Workflow `timeout-minutes: 350` fired | Re-run; post-job cache save ran, progress is intact |
| Run cancelled immediately after new push | Old behavior (no concurrency block) — now fixed | Upgrade to current workflow |
| `BUILD FAILURE` with `SessionNotCreated` on all tests | Chrome/ChromeDriver incompatibility | Check `Verify Chrome installation` step; may need to pin Chrome version |

---

## Self-Hosted Runners (No 6-Hour Limit)

If the UPC list is large enough that 6 hours is consistently insufficient, consider
using a self-hosted runner. Self-hosted runners do not have GitHub's 6-hour
execution limit.

To use a self-hosted runner, change:

```yaml
runs-on: ubuntu-latest
```

to:

```yaml
runs-on: self-hosted
```

and register a runner machine in **Settings → Actions → Runners**.

With a self-hosted runner you can also increase `timeout-minutes` to `1440` (24h)
or higher.
