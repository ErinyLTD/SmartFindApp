#!/bin/bash
# ============================================================
# check-imports.sh
# Scans Gradle cached JARs/AARs for available classes and
# checks whether a specific class can be imported.
#
# Usage:
#   ./check-imports.sh                          # list all classes from project deps
#   ./check-imports.sh ExistingUniqueWorkPolicy  # search for a specific class
#   ./check-imports.sh ExistingWorkPolicy         # search for a specific class
# ============================================================

set -euo pipefail

GRADLE_CACHE="$HOME/.gradle/caches"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SEARCH_TERM="${1:-}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# --- Extract dependency coordinates from build.gradle.kts ---
echo -e "${CYAN}=== Project dependencies (from app/build.gradle.kts) ===${NC}"
echo ""
grep -oP '(implementation|api|ksp|testImplementation|androidTestImplementation)\("([^"]+)"\)' \
    "$PROJECT_DIR/app/build.gradle.kts" | while IFS= read -r line; do
    echo "  $line"
done
echo ""

# --- Collect all JARs from Gradle cache for project dependencies ---
echo -e "${CYAN}=== Scanning Gradle cache for dependency JARs/AARs ===${NC}"
echo ""

# Extract group:artifact:version triples
DEPS=$(grep -oP '"[^"]+"' "$PROJECT_DIR/app/build.gradle.kts" \
    | tr -d '"' \
    | grep ':' \
    | grep -v 'com.android' \
    | grep -v 'org.jetbrains.kotlin' \
    | grep -v 'com.google.devtools.ksp')

TMPDIR_WORK=$(mktemp -d)
JAR_LIST="$TMPDIR_WORK/jars.txt"
CLASS_LIST="$TMPDIR_WORK/classes.txt"
touch "$JAR_LIST" "$CLASS_LIST"

# Find JARs in the Gradle transforms/files caches
for dep in $DEPS; do
    GROUP=$(echo "$dep" | cut -d: -f1)
    ARTIFACT=$(echo "$dep" | cut -d: -f2)
    VERSION=$(echo "$dep" | cut -d: -f3)

    # Convert group to path (e.g., androidx.work -> androidx/work or androidx.work)
    GROUP_PATH_DOT="$GROUP"
    GROUP_PATH_SLASH=$(echo "$GROUP" | tr '.' '/')

    # Search in modules-2/files-* and transforms-* caches
    find "$GRADLE_CACHE" -path "*/$GROUP_PATH_SLASH/$ARTIFACT/$VERSION*" \
        \( -name "*.jar" -o -name "classes.jar" \) 2>/dev/null >> "$JAR_LIST" || true
    find "$GRADLE_CACHE" -path "*/$GROUP_PATH_DOT/$ARTIFACT/$VERSION*" \
        \( -name "*.jar" -o -name "classes.jar" \) 2>/dev/null >> "$JAR_LIST" || true

    # Also search by artifact name broadly (handles transformed AARs)
    find "$GRADLE_CACHE" -path "*$ARTIFACT*$VERSION*" \
        -name "*.jar" 2>/dev/null >> "$JAR_LIST" || true
done

# Deduplicate
sort -u "$JAR_LIST" -o "$JAR_LIST"

JAR_COUNT=$(wc -l < "$JAR_LIST")
echo -e "  Found ${GREEN}$JAR_COUNT${NC} JARs in Gradle cache"
echo ""

# --- List all classes from those JARs ---
echo -e "${CYAN}=== Extracting class names from JARs ===${NC}"
echo ""

while IFS= read -r jar; do
    if [ -f "$jar" ]; then
        # List .class files, convert path to Java class name
        jar tf "$jar" 2>/dev/null \
            | grep '\.class$' \
            | grep -v '\$' \
            | sed 's|/|.|g' \
            | sed 's|\.class$||' \
            >> "$CLASS_LIST"
    fi
done < "$JAR_LIST"

sort -u "$CLASS_LIST" -o "$CLASS_LIST"
TOTAL_CLASSES=$(wc -l < "$CLASS_LIST")
echo -e "  Found ${GREEN}$TOTAL_CLASSES${NC} top-level classes across all dependency JARs"
echo ""

# --- Search or display ---
if [ -n "$SEARCH_TERM" ]; then
    echo -e "${CYAN}=== Searching for: ${YELLOW}$SEARCH_TERM${NC} ==="
    echo ""

    MATCHES=$(grep -i "$SEARCH_TERM" "$CLASS_LIST" || true)

    if [ -n "$MATCHES" ]; then
        echo -e "${GREEN}Found matching classes:${NC}"
        echo "$MATCHES" | while IFS= read -r cls; do
            echo -e "  ${GREEN}✓${NC} $cls"
        done
        echo ""
        echo -e "${GREEN}You can import any of the above in your Kotlin/Java code.${NC}"
    else
        echo -e "${RED}✗ No class matching '$SEARCH_TERM' found in any project dependency.${NC}"
        echo ""

        # Suggest similar classes
        echo -e "${YELLOW}Similar classes in dependencies:${NC}"
        # Try partial match on the last word segment
        PARTIAL=$(echo "$SEARCH_TERM" | grep -oP '[A-Z][a-z]+' | head -3 | tr '\n' '|' | sed 's/|$//')
        if [ -n "$PARTIAL" ]; then
            SIMILAR=$(grep -iE "$PARTIAL" "$CLASS_LIST" | head -20 || true)
            if [ -n "$SIMILAR" ]; then
                echo "$SIMILAR" | while IFS= read -r cls; do
                    echo -e "  ${YELLOW}~${NC} $cls"
                done
            else
                echo "  (no similar classes found)"
            fi
        fi
        echo ""
        echo -e "${RED}The class '$SEARCH_TERM' does not exist in your declared dependencies.${NC}"
        echo -e "Check for typos or add the correct dependency to build.gradle.kts."
    fi
else
    # No search term — dump all classes grouped by package prefix
    echo -e "${CYAN}=== All available classes (grouped by top-level package) ===${NC}"
    echo ""
    echo -e "  Printing to: ${YELLOW}$TMPDIR_WORK/all-classes.txt${NC}"
    cp "$CLASS_LIST" "$TMPDIR_WORK/all-classes.txt"
    echo ""

    # Show summary by top-level package
    echo -e "${CYAN}=== Class count by package prefix ===${NC}"
    echo ""
    awk -F. '{print $1"."$2"."$3}' "$CLASS_LIST" \
        | sort | uniq -c | sort -rn | head -30 | while IFS= read -r line; do
        echo "  $line"
    done
    echo ""
    echo -e "Full class list saved to: ${YELLOW}$TMPDIR_WORK/all-classes.txt${NC}"
    echo -e "You can search it with: ${CYAN}grep -i 'ClassName' $TMPDIR_WORK/all-classes.txt${NC}"
fi

# --- Also check for ExistingWorkPolicy variants specifically ---
if [ -z "$SEARCH_TERM" ] || echo "$SEARCH_TERM" | grep -qi "existing"; then
    echo ""
    echo -e "${CYAN}=== WorkManager policy classes ===${NC}"
    WORK_POLICIES=$(grep -i "ExistingWork\|ExistingPeriodic\|ExistingUnique" "$CLASS_LIST" || true)
    if [ -n "$WORK_POLICIES" ]; then
        echo "$WORK_POLICIES" | while IFS= read -r cls; do
            echo -e "  ${GREEN}✓${NC} $cls"
        done
    else
        echo -e "  ${YELLOW}(WorkManager JARs may not be cached yet — run a Gradle sync first)${NC}"
    fi
fi

echo ""
echo -e "${CYAN}Done.${NC}"

# Cleanup
rm -rf "$TMPDIR_WORK"
