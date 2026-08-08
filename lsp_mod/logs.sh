#!/system/bin/sh
LOG=$(ls -t /data/adb/lspd/log/modules_*.log | head -1)
echo "=== $LOG ==="
grep -E "PicoWinLimit|PicoTrackerLimit|winlimit|trackerlimit" "$LOG" | tail -40
echo "=== errors ==="
grep -iE "error|exception|failed" "$LOG" | grep -iE "pico" | tail -20
