"""Sync modules.apk_path with the real install path after a reinstall."""
import sqlite3
import subprocess
import sys

DB = r"work\m.db"

def pm_path(pkg: str) -> str | None:
    out = subprocess.run(["adb", "shell", "pm", "path", pkg],
                         capture_output=True, text=True).stdout.strip()
    for line in out.splitlines():
        line = line.strip()
        if line.startswith("package:"):
            return line[len("package:"):]
    return None

con = sqlite3.connect(DB)
changed = False
for mid, pkg, cur in con.execute(
        "select mid, module_pkg_name, apk_path from modules where mid > 1").fetchall():
    real = pm_path(pkg)
    if not real:
        print(f"  !! {pkg}: not installed")
        continue
    if real != cur:
        con.execute("update modules set apk_path=? where mid=?", (real, mid))
        print(f"  ~ {pkg}\n      {cur}\n   -> {real}")
        changed = True
    else:
        print(f"  = {pkg} ok")

if changed:
    con.commit()
    print("committed")
else:
    print("nothing to do")
con.close()
