# Kinsey Scrapy Automation Bot

A Selenium/TestNG-based web-scraping automation framework that processes large
UPC lists against the Kinsey product catalogue and records add-to-cart
availability and product detail data.

---

## Table of Contents

1. [Project structure](#project-structure)
2. [Running locally](#running-locally)
3. [GitHub Actions CI/CD](#github-actions-cicd)
   - [Workflows](#workflows)
   - [Timeout behaviour on GitHub-hosted runners](#timeout-behaviour-on-github-hosted-runners)
   - [Heartbeat / inactivity protection](#heartbeat--inactivity-protection)
   - [UPC progress checkpointing](#upc-progress-checkpointing)
   - [Recommendations for long-running scrapes](#recommendations-for-long-running-scrapes)
4. [Configuration](#configuration)

---

## Project structure

```
.
├── .github/workflows/
│   ├── ci-cd-testng-parallel.yml   # CI for master branch
│   ├── manual.yml                  # CI for development-shivang branch
│   └── merge-master-to-branches.yml
├── src/
│   ├── main/java/framework/        # Framework core (driver, pages, utils, config)
│   └── test/java/framework/tests/ # TestNG test classes
├── ScrapingOutputResults/          # Runtime output (CSVs, reports, screenshots)
├── testng-parallel.xml             # Parallel suite definition (6 threads)
├── testng-sequential.xml           # Sequential suite definition
└── pom.xml
```

---

## Running locally

```bash
# Compile
mvn clean compile -B

# Run the parallel suite (headless)
mvn test -DsuiteXmlFile=testng-parallel.xml -Dheadless=true -B

# Run the sequential suite
mvn test -DsuiteXmlFile=testng-sequential.xml -Dheadless=true -B
```

Required system properties / environment variables are loaded from
`src/main/resources/config.properties` (see [Configuration](#configuration)).

---

## GitHub Actions CI/CD

### Workflows

| File | Trigger branches | Purpose |
|------|-----------------|---------|
| `ci-cd-testng-parallel.yml` | `master` | Full parallel suite on master |
| `manual.yml` | `development-shivang` | Full parallel suite on development-shivang |
| `merge-master-to-branches.yml` | `workflow_dispatch` | Sync master → main & development-shivang |

### Timeout behaviour on GitHub-hosted runners

> **Important:** GitHub-hosted runners (e.g. `ubuntu-latest`) enforce a
> **hard 6-hour (360-minute) maximum** per job, regardless of the
> `timeout-minutes` value specified in the workflow file.  Setting
> `timeout-minutes: 1440` (24 h) does **not** give the job 24 hours — the
> runner will be silently terminated at the 6-hour platform limit.

Both CI workflows therefore use `timeout-minutes: 350` so the configured
limit is slightly below the hard platform ceiling, making any timeout
observable and clearly attributable in the run log rather than being a
mysterious platform kill.

#### Why the old value caused unexpected timeouts

The original workflows set `timeout-minutes: 1440`.  Because this exceeds
the 6-hour platform ceiling, GitHub kills the runner at ~360 minutes.  The
run is then marked as *cancelled* rather than *timed-out*, and no
`timeout-minutes`-exceeded message appears in the logs — making the root
cause difficult to diagnose.

### Heartbeat / inactivity protection

GitHub Actions also kills any job that produces **no log output for
10 consecutive minutes**.  A long scraping run that is actively processing
UPCs can easily exceed this without printing anything.

Both CI workflows start a background *heartbeat* process that prints a
timestamped line every 5 minutes:

```
[heartbeat] 2025-06-01T10:05:00Z - test suite still running...
```

The heartbeat is stopped cleanly after `mvn test` exits, and the Maven
process exit code is preserved so the step still reports failure correctly.

### UPC progress checkpointing

The `ProgressTracker` class writes processed UPC identifiers to
`ScrapingOutputResults/progress/` as work proceeds.  Two cache steps
bracket the test run:

1. **Restore** — loads the most recent progress snapshot from the Actions
   cache before the test starts, enabling the suite to resume from where a
   previous run left off.
2. **Save** (`if: always()`) — saves the current progress snapshot after
   the test finishes, even if the job timed out or failed.

Cache key pattern:

```
upc-progress-<github.ref>-<github.run_id>
```

Restore fallback prefix: `upc-progress-<github.ref>-` (matches any prior
run on the same branch).

### Recommendations for long-running scrapes

| Concern | Recommendation |
|---------|---------------|
| Run exceeds 6 hours | Split the UPC list into smaller batches and use multiple workflow runs or a matrix strategy |
| Silent hang | The heartbeat makes hangs visible; additionally check Surefire reports uploaded as artifacts |
| Diagnosing a failure | Inspect the *Print runner diagnostics* step at the top of each run for environment details, and the `[diagnostics]` lines wrapping the Maven invocation for exact start/end timestamps and exit code |
| Retrying after timeout | Re-run the workflow — the progress checkpoint will be restored and already-processed UPCs will be skipped |

---

## Configuration

Framework parameters are read from `src/main/resources/config.properties`.

| Key | Default | Description |
|-----|---------|-------------|
| `base.url` | *(required)* | Target site URL |
| `username` | *(required)* | Login username |
| `password` | *(required)* | Login password |
| `upc.file` | *(required)* | Path to newline-delimited UPC input file |
| `batch.size` | `250` | UPCs per parallel batch |
| `headless` | `false` | Run Chrome headless |
| `scraping.output.dir` | `ScrapingOutputResults` | Root output directory |
| `network.retry.count` | `1` | Retries on transient network errors |
| `network.retry.sleep.ms` | `1500` | Sleep between retries (ms) |
| `blocked.backoff.ms` | `5000` | Pause after a BLOCKED/CAPTCHA result (ms) |
