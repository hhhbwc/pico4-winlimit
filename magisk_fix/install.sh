set -e
MOD=/data/adb/modules/pico_winlimit_fix
mkdir -p "$MOD"
cp /sdcard/mf_module.prop "$MOD/module.prop"
cp /sdcard/mf_service.sh  "$MOD/service.sh"
chmod 755 "$MOD/service.sh"
chmod 644 "$MOD/module.prop"
chown root:root "$MOD/module.prop" "$MOD/service.sh"
echo "--- installed ---"
ls -la "$MOD"
echo "--- all modules ---"
ls /data/adb/modules/
