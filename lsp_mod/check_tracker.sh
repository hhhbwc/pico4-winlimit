#!/system/bin/sh
# Read back the stored Swift upper limit and the module's view of it.
echo "=== stored config (via logcat trace) ==="
logcat -d -s SwiftUtils:I | grep "upper.limit" | tail -3

echo ""
echo "=== module log ==="
LOG=$(ls -t /data/adb/lspd/log/modules_*.log | head -1)
grep -E "PicoTrackerLimit" "$LOG" | tail -10

echo ""
echo "=== recent swift crashes ==="
logcat -d -b crash | grep -iE "pvr.swift|picoxr.trackerlimit" | tail -5
echo "(end)"
