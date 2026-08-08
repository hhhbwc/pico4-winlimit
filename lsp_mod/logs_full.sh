#!/system/bin/sh
LOG=$(ls -t /data/adb/lspd/log/modules_*.log | head -1)
echo "=== full context around failures in $LOG ==="
grep -n -A 12 "showLimitTogglePopup replacement failed" "$LOG" | tail -40
