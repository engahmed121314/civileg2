#!/bin/bash
# Complete restore and proper fix script
# Run from any directory - uses absolute paths
set -e

CDIR="/home/z/my-project/civileg2"
cd "$CDIR"

echo "=== Current commit: $(git log --oneline -1) ==="
echo "=== Files modified vs HEAD: ==="
git diff --name-only HEAD 2>&1 | head -30

echo ""
echo "=== Restoring ALL modified files from HEAD ==="
# Restore ALL modified files from the last commit
for f in $(git diff --name-only HEAD); do
    git checkout HEAD -- "$f"
    echo "  Restored: $f"
done

echo ""
echo "=== Verify clean state ==="
CHANGES=$(git diff --name-only HEAD 2>&1)
if [ -z "$CHANGES" ]; then
    echo "All files restored to HEAD state."
else
    echo "WARNING: Still modified: $CHANGES"
fi