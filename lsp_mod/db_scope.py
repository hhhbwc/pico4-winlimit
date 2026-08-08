import sqlite3, shutil

shutil.copy('c.db', 'c.db.bak2')
c = sqlite3.connect('c.db')
cur = c.cursor()

# module mid for our patcher
cur.execute("SELECT mid FROM modules WHERE module_pkg_name='com.picoxr.patcher'")
mid = cur.fetchone()[0]
print("mid =", mid)

# Add 'android' (system framework) and 'system' to scope, in case SystemExt
# (uid=1000 persistent) requires it.
for app in ('android', 'system'):
    cur.execute("INSERT OR IGNORE INTO scope (mid, app_pkg_name, user_id) VALUES (?,?,0)", (mid, app))
    print("scope +", app)

c.commit()
cur.execute("SELECT * FROM scope")
print("scope now:", cur.fetchall())
c.close()
