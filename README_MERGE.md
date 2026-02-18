# Merge Master Branch to Main and Development-Shivang

## Quick Start

This PR contains the setup needed to merge all changes from the `master` branch into both `main` and `development-shivang` branches.

### What Has Been Done

✅ All changes from master (55 files, 4,530 lines) have been locally merged into:
- `main` branch  
- `development-shivang` branch

### What You Need to Do

**After merging this PR**, choose one of these options to complete the merge:

#### Option 1: GitHub Actions (Easiest) ⭐

1. Go to the **Actions** tab in your repository
2. Select "**Merge Master to Main and Development Branches**" workflow  
3. Click "**Run workflow**"
4. Done! The workflow will automatically merge and push changes

#### Option 2: Run the Shell Script

```bash
git checkout main
./merge-master-to-branches.sh
```

#### Option 3: Manual Git Commands

```bash
# Merge and push to main
git checkout main
git merge master --allow-unrelated-histories --no-edit
git push origin main

# Merge and push to development-shivang
git checkout development-shivang  
git merge master --allow-unrelated-histories --no-edit
git push origin development-shivang
```

### What's Included in This PR

1. **GitHub Actions Workflow** (`.github/workflows/merge-master-to-branches.yml`)
   - Automated workflow to merge master into both branches
   - Can be triggered manually from the Actions tab

2. **Shell Script** (`merge-master-to-branches.sh`)
   - Bash script for command-line execution
   - Performs merges and pushes automatically

3. **Documentation** (`MERGE_DOCUMENTATION.md`)
   - Detailed documentation of the merge process
   - List of all files being merged
   - Verification steps

### Changes Being Merged

The following components from `master` will be merged:

- **Java Test Framework** (21 source files)
  - Selenium WebDriver setup
  - Page Object Model implementation
  - TestNG test suites
  - Utility classes for CSV handling, screenshots, waits, etc.

- **Configuration Files**
  - Maven POM configuration
  - TestNG configurations (parallel & sequential)
  - Application properties

- **Test Results** 
  - Sample scraping results
  - HTML test reports

See `MERGE_DOCUMENTATION.md` for the complete list.

### Why This Approach?

The repository had different commit histories in each branch (unrelated histories), so the merges required special handling with the `--allow-unrelated-histories` flag. This PR provides multiple convenient ways to complete the merge safely.

### Questions?

Refer to `MERGE_DOCUMENTATION.md` for detailed information about the merge process and verification steps.
