from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path('android-nadaye')
APP = ROOT / 'app'
JAVA = APP / 'src/main/java/com/nadaye/beheshti'
ANDROID_NS = 'http://schemas.android.com/apk/res/android'
A = '{%s}' % ANDROID_NS
ET.register_namespace('android', ANDROID_NS)

# 1) Final release identity after the premium overlay.
gradle = APP / 'build.gradle'
s = gradle.read_text(encoding='utf-8')
s = re.sub(r'versionCode\s+\d+', 'versionCode 6', s)
s = re.sub(r"versionName\s+['\"][^'\"]+['\"]", "versionName '4.1.0'", s)
gradle.write_text(s, encoding='utf-8')

# 2) Manifest privacy and resilient rescheduling after clock/package changes.
manifest = APP / 'src/main/AndroidManifest.xml'
tree = ET.parse(manifest)
root = tree.getroot()
perms = {x.get(A+'name') for x in root.findall('uses-permission')}
for p in ['android.permission.WAKE_LOCK', 'android.permission.ACCESS_NETWORK_STATE']:
    if p not in perms:
        el = ET.Element('uses-permission')
        el.set(A+'name', p)
        root.insert(0, el)

application = root.find('application')
if application is None:
    raise SystemExit('application element missing')
application.set(A+'allowBackup', 'false')
application.set(A+'fullBackupContent', 'false')

boot = None
for r in application.findall('receiver'):
    if r.get(A+'name') in ('.BootReceiver', 'com.nadaye.beheshti.BootReceiver'):
        boot = r
        break
if boot is None:
    boot = ET.SubElement(application, 'receiver')
    boot.set(A+'name', '.BootReceiver')
    boot.set(A+'exported', 'true')
intent = boot.find('intent-filter')
if intent is None:
    intent = ET.SubElement(boot, 'intent-filter')
actions = {x.get(A+'name') for x in intent.findall('action')}
for action in [
    'android.intent.action.BOOT_COMPLETED',
    'android.intent.action.TIME_SET',
    'android.intent.action.TIMEZONE_CHANGED',
    'android.intent.action.DATE_CHANGED',
    'android.intent.action.MY_PACKAGE_REPLACED',
]:
    if action not in actions:
        x = ET.SubElement(intent, 'action')
        x.set(A+'name', action)
manifest.write_text(ET.tostring(root, encoding='unicode'), encoding='utf-8')

# 3) Android 12+ exact-alarm access. Do not overwrite the premium UI or its own lifecycle methods.
main = JAVA / 'MainActivity.java'
m = main.read_text(encoding='utf-8')
if 'void ensureExactAlarmAccess()' not in m:
    helper = r'''
    void ensureExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        } catch (Exception ignored) { }
    }

'''
    marker = '    void immersive(boolean yes)'
    if marker in m:
        m = m.replace(marker, helper + marker, 1)
    else:
        pos = m.rfind('}')
        if pos < 0:
            raise SystemExit('MainActivity brace missing')
        m = m[:pos] + helper + m[pos:]

# Premium onCreate can change between overlay revisions. Inject after super.onCreate(...) rather than
# relying on an older requestNeededPermissions marker.
prefix = m.split('void ensureExactAlarmAccess()', 1)[0]
if 'ensureExactAlarmAccess();' not in prefix:
    m2, n = re.subn(r'(super\.onCreate\s*\([^;]+;)', r'\1\n        ensureExactAlarmAccess();\n        try { AlarmReceiver.scheduleToday(this); } catch (Exception ignored) { }', m, count=1)
    if n == 0:
        raise SystemExit('could not locate MainActivity super.onCreate call')
    m = m2

m = m.replace('نسخه ۴٫۰', 'نسخه ۴٫۱').replace('نسخه ۴.۰', 'نسخه ۴.۱')
main.write_text(m, encoding='utf-8')

# 4) Add Android 8+ adaptive launcher resources while preserving premium artwork.
values = APP / 'src/main/res/values'
values.mkdir(parents=True, exist_ok=True)
(values / 'launcher_colors.xml').write_text(
    '<?xml version="1.0" encoding="utf-8"?>\n<resources><color name="launcher_bg">#FAF6EE</color></resources>\n',
    encoding='utf-8')
p = APP / 'src/main/res/mipmap-anydpi-v26'
p.mkdir(parents=True, exist_ok=True)
for name in ['ic_launcher', 'ic_launcher_round']:
    (p / f'{name}.xml').write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/launcher_bg"/><foreground android:drawable="@drawable/ic_launcher"/></adaptive-icon>\n',
        encoding='utf-8')

# 5) Fail fast if the post-overlay fixes are missing.
final_gradle = gradle.read_text(encoding='utf-8')
final_main = main.read_text(encoding='utf-8')
final_manifest = manifest.read_text(encoding='utf-8')
assert 'versionCode 6' in final_gradle
assert "versionName '4.1.0'" in final_gradle
assert 'ensureExactAlarmAccess();' in final_main
assert 'android.intent.action.TIMEZONE_CHANGED' in final_manifest
assert 'allowBackup="false"' in final_manifest
print('Nadaye v4.1 post-overlay hardening applied successfully')
