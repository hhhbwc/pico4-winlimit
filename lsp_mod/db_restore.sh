#!/system/bin/sh
# Restore a patched modules_config.db and drop stale WAL/SHM sidecars.
SRC=/data/local/tmp/m.db
DST=/data/adb/lspd/config/modules_config.db

if [ ! -f "$SRC" ]; then
  echo "missing $SRC"
  exit 1
fi

cp "$DST" "${DST}.bak.$(date +%s)" 2>/dev/null
cp "$SRC" "$DST"
chown 0:0 "$DST"
chmod 660 "$DST"
rm -f "${DST}-wal" "${DST}-shm"
echo "restored:"
ls -l "$DST"*
