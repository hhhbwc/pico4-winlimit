#!/system/bin/sh
# PICO WinLimit Boot Fix
#
# Problem: com.picovr.systemext is a PERSISTENT system app (uid 1000). It
# starts during early boot, in the window between
#   "Injected Vector framework into system_server"   (~t+1s)
# and
#   "System services are ready. Mapping modules and scopes."  (~t+3s)
# Because the scope table is not loaded yet when SystemExt spawns, Vector
# skips injecting it and the window-limit module never loads.
#
# Fix: after boot completes AND Vector has mapped its scopes, restart
# SystemExt exactly once. Android respawns it automatically (PERSISTENT),
# and this time the scope table is ready, so injection succeeds.

MODDIR=${0%/*}
LOG=/data/local/tmp/pico_winlimit_fix.log
TAG="PicoWinLimitFix"

say() {
  echo "$(date '+%Y-%m-%d %H:%M:%S') $1" >> "$LOG"
  /system/bin/log -p i -t "$TAG" "$1" 2>/dev/null
}

: > "$LOG"
say "service.sh started"

# 1) Wait for boot to complete (cap ~180s).
i=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ $i -lt 360 ]; do
  sleep 0.5
  i=$((i + 1))
done
say "boot_completed after ${i} half-seconds"

# 2) Wait for Vector to finish mapping modules and scopes. Detect the real
#    signal in its own log rather than guessing a fixed delay.
i=0
mapped=0
while [ $i -lt 120 ]; do
  if grep -qs "Mapping modules and scopes" /data/adb/lspd/log/verbose_*.log 2>/dev/null; then
    mapped=1
    break
  fi
  sleep 0.5
  i=$((i + 1))
done
say "scope mapping detected=${mapped} after ${i} half-seconds"

# 3) Small settle margin so the scope cache is fully populated.
sleep 3

# 4) If the module is already loaded, there is nothing to fix.
if grep -qs "PicoWinLimit: installed" /data/adb/lspd/log/verbose_*.log 2>/dev/null; then
  say "module already injected, no restart needed"
  exit 0
fi

# 5) Restart SystemExt once. It is PERSISTENT, so Android brings it back.
PID=$(pidof com.picovr.systemext)
if [ -n "$PID" ]; then
  say "killing com.picovr.systemext (pid $PID)"
  kill "$PID" 2>/dev/null
else
  say "com.picovr.systemext not running, nothing to kill"
fi

# 6) Verify the module got injected on the respawn.
sleep 8
if grep -qs "PicoWinLimit: installed" /data/adb/lspd/log/verbose_*.log 2>/dev/null; then
  say "SUCCESS: window-limit module injected"
else
  say "WARNING: module still not injected, retrying once"
  PID=$(pidof com.picovr.systemext)
  [ -n "$PID" ] && kill "$PID" 2>/dev/null
  sleep 8
  if grep -qs "PicoWinLimit: installed" /data/adb/lspd/log/verbose_*.log 2>/dev/null; then
    say "SUCCESS on retry"
  else
    say "FAILED: module not injected after retry"
  fi
fi

say "service.sh done"
