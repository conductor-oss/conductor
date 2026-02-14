#!/bin/bash
# PR #749 Build Monitor - Reports to Claude Code via status file

STATUS_FILE="$HOME/.claude-monitor/pr749_build.json"
PR_URL="https://github.com/conductor-oss/conductor/pull/749"
CONDUCTOR_DIR="/Users/nthmost/projects/git/conductor-oss/conductor"

mkdir -p "$(dirname "$STATUS_FILE")"

echo "🔍 PR Monitor started for PR #749"
echo "📁 Status file: $STATUS_FILE"
echo "🔗 PR URL: $PR_URL"
echo ""

while true; do
  echo "⏱️  Checking PR status at $(date '+%H:%M:%S')..."

  cd "$CONDUCTOR_DIR"

  # Get PR checks status
  # Exit codes: 0 = pass, 1 = fail, 8 = pending, >8 = error
  PR_OUTPUT=$(gh pr checks 749 2>&1)
  EXIT_CODE=$?

  # If we got output, process it
  if [ -n "$PR_OUTPUT" ] && [ $EXIT_CODE -le 8 ]; then

    # Check if all checks passed
    if [ $EXIT_CODE -eq 0 ] && ! echo "$PR_OUTPUT" | grep -qi "fail\|pending"; then
      STATUS="completed"
      MESSAGE="✅ All checks passed!"
      echo "$MESSAGE"

      # Write final status
      cat > "$STATUS_FILE" << EOF
{
  "task_name": "PR #749 Build Monitor",
  "status": "$STATUS",
  "message": "$MESSAGE",
  "pr_url": "$PR_URL",
  "updated_at": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "details": "All CI checks have completed successfully",
  "tiny_title": "✅ Built"
}
EOF
      echo "✨ Build succeeded! Exiting monitor."
      exit 0

    elif echo "$PR_OUTPUT" | grep -qi "fail"; then
      STATUS="failed"
      MESSAGE="❌ Some checks failed"
      echo "$MESSAGE"

      # Extract failed checks
      FAILED=$(echo "$PR_OUTPUT" | grep -i "fail" | head -3 | sed 's/"/\\"/g')

      cat > "$STATUS_FILE" << EOF
{
  "task_name": "PR #749 Build Monitor",
  "status": "$STATUS",
  "message": "$MESSAGE",
  "pr_url": "$PR_URL",
  "updated_at": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "failed_checks": "$FAILED",
  "tiny_title": "❌ Failed"
}
EOF
      echo "💥 Build failed! Exiting monitor."
      exit 1

    else
      STATUS="pending"
      MESSAGE="⏳ Checks are still running..."
      echo "$MESSAGE"

      # Count pending/running
      PENDING_COUNT=$(echo "$PR_OUTPUT" | grep -c "pending" || echo "?")

      cat > "$STATUS_FILE" << EOF
{
  "task_name": "PR #749 Build Monitor",
  "status": "$STATUS",
  "message": "$MESSAGE",
  "pr_url": "$PR_URL",
  "updated_at": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "pending_count": "$PENDING_COUNT",
  "tiny_title": "⏳ Building"
}
EOF
    fi
  else
    echo "⚠️  Error checking PR status"
    cat > "$STATUS_FILE" << EOF
{
  "task_name": "PR #749 Build Monitor",
  "status": "error",
  "message": "Error querying GitHub API",
  "pr_url": "$PR_URL",
  "updated_at": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "tiny_title": "⚠️ Error"
}
EOF
  fi

  # Wait 45 seconds before next check
  echo "   Waiting 45 seconds..."
  echo ""
  sleep 45
done
