#!/bin/bash
# Validates the spec in about a minute: every model, mutant and witness, and a sample of generated
# programs. With a server URL it also runs 40 tests against that server.
#   TLA2TOOLS=/path/to/tla2tools.jar ./verify.sh [server-url]
cd "$(dirname "$0")"
START=$SECONDS
python3 check.py --jobs 16 | grep -v 'reached$\|caught by\|caught: outcome'
CHECK=${PIPESTATUS[0]}
echo "check.py: exit $CHECK after $((SECONDS - START))s"
python3 enumerate.py --size 3 --limit 100 --timeout 20 --jobs 16 --seed $RANDOM
SWEPT=$?
if [ -n "$1" ]; then
  python3 server_check.py --server "$1" --tests 40 --size 3 --jobs 6 --settle 1 --deadline 25 --seed $RANDOM
  echo "server_check.py: any mismatches are listed above"
fi
echo "total $((SECONDS - START))s"
[ $CHECK -eq 0 ] && [ $SWEPT -eq 0 ]
