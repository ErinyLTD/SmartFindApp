#!/bin/bash
# Quick class search in Gradle cached JARs
# Usage: ./check-class.sh <ClassName>
set -euo pipefail

GRADLE_CACHE="$HOME/.gradle/caches"
SEARCH="${1:-ExistingWorkPolicy}"

echo "Searching for '$SEARCH' in all cached JARs..."
echo ""

find "$GRADLE_CACHE" -name "*.jar" -print0 2>/dev/null | while IFS= read -r -d '' jar; do
    MATCHES=$(jar tf "$jar" 2>/dev/null | grep -i "$SEARCH" || true)
    if [ -n "$MATCHES" ]; then
        echo "=== JAR: $jar ==="
        echo "$MATCHES" | sed 's|/|.|g' | sed 's|\.class$||'
        echo ""
    fi
done

echo "--- Search complete ---"
