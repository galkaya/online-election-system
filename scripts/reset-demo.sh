#!/usr/bin/env sh
# Deletes the demo database so that the next demo start seeds fresh data.
# It only ever touches <project>/data/demo/election-demo.* and refuses to run without --confirm-reset.
# Usage:  ./scripts/reset-demo.sh --confirm-reset
set -eu

project_root="$(cd "$(dirname "$0")/.." && pwd)"
demo_dir="$project_root/data/demo"

if [ ! -f "$project_root/pom.xml" ]; then
    echo "Refusing to run: $project_root does not look like the project folder (no pom.xml)." >&2
    exit 1
fi
if [ "${1:-}" != "--confirm-reset" ]; then
    echo "This deletes the demo database in $demo_dir (all demo votes, elections and accounts)."
    echo "Nothing was deleted. Run again with --confirm-reset to proceed."
    exit 2
fi
if [ ! -d "$demo_dir" ]; then
    echo "There is no demo database yet. Nothing to reset."
    exit 0
fi
if [ -f "$demo_dir/election-demo.lock.db" ]; then
    echo "The demo database appears to be in use. Stop the application first, then run the reset again." >&2
    exit 1
fi
for file in "$demo_dir"/election-demo.*; do
    [ -e "$file" ] || continue
    rm -f -- "$file"
    echo "Deleted $file"
done
echo "Demo database reset. Start the demo again to seed fresh data."
