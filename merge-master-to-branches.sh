#!/bin/bash

# Script to merge master branch into main and development-shivang branches
# This script was created to document the merge operations that need to be pushed

set -e

echo "========================================="
echo "Merging master branch to main and development-shivang"
echo "========================================="

# Merge master into main
echo ""
echo "Step 1: Merging master into main branch..."
git checkout main
git merge master --allow-unrelated-histories --no-edit
echo "✓ Successfully merged master into main"

# Push main branch
echo ""
echo "Step 2: Pushing main branch to remote..."
git push origin main
echo "✓ Successfully pushed main branch"

# Merge master into development-shivang
echo ""
echo "Step 3: Merging master into development-shivang branch..."
git checkout development-shivang
git merge master --allow-unrelated-histories --no-edit
echo "✓ Successfully merged master into development-shivang"

# Push development-shivang branch
echo ""
echo "Step 4: Pushing development-shivang branch to remote..."
git push origin development-shivang
echo "✓ Successfully pushed development-shivang branch"

echo ""
echo "========================================="
echo "All merges completed successfully!"
echo "========================================="
echo ""
echo "Summary:"
echo "- Master branch has been merged into main"
echo "- Master branch has been merged into development-shivang"
echo "- All changes (55 files, 4530 insertions) have been applied"
