from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path('android-nadaye')
APP = ROOT / 'app'
JAVA = APP / 'src/main/java/com/nadaye/beheshti'
ANDROID_NS = 'http://schemas.android.com/apk/res/android'
A = '{%s}' % ANDROID_NS
ET.register_namespace('android', ANDROID_NS)

# 1) Release identity and build hardening. The premium overlay currently writes v4 metadata;
# this post-overlay layer owns the final build identity so future overlays cannot roll it back.
gradle = APP / 'build.gradle'
s = gradle.read_text(encoding='utf-8')
s = re.sub(r'versionCode\s+\d+', 'versionCode 6', s)
s = re.sub(r"versionName\s+['\"][^'\"]+['\"]", "versionName '4.1.0'", s)
# Keep resource shrinking off because the premium UI intentionally resolves some resources dynamically.
# R8 is enabled only if an explicit keep-safe release block already exists; functionality wins over size.
gradle.write_text(s, encoding='utf-8')

# 2) Manifest privacy + resilient clock/boot rescheduling.
manifest = APP / 'src/main/AndroidManifest.xml'
tree = ET.parse(manifest)
root = tree.getroot()
perms = {x.get(A+'name') for x in root.findall('uses-permission')}
for p in [
    'android.permission.WAKE_LOCK',
    'android.permission.ACCESS_NETWORK_STATE',
]:
    if p not in perms:
        el = ET.Element('uses-permission')
        el.set(A+'name', p)
        root.insert(0, el)

application = root.find('application')
if application is None:
    raise SystemExit('application element missing')
application.set(A+'allowBackup', 'false')
application.set(A+'fullBackupContent', 'false')

# The premium overlay already owns BootReceiver. Ensure it reacts to every clock/package event
# that can invalidate previously calculated prayer alarms.
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
    'android.intent.action.LOCKED_BOOT_COMPLETED',
    'android.intent.action.TIME_SET',
    'android.intent.action.TIMEZONE_CHANGED',
    'android.intent.action.DATE_CHANGED',
    'android.intent.action.MY_PACKAGE_REPLACED',
]:
    if action not in actions:
        x = ET.SubElement(intent, 'action')
        x.set(A+'name', action)
manifest.write_text(ET.tostring(root, encoding='unicode'), encoding='utf-8')

# 3) MainActivity: request Android 12+ exact-alarm special access and self-heal alarms
# whenever the user returns to the app. This is deliberately idempotent.
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

    @Override protected void onResume() {
        super.onResume();
        try {
            AlarmReceiver.scheduleToday(this);
        } catch (Exception ignored) { }
    }

'''
    marker = '    void immersive(boolean yes)'
    if marker in m:
        m = m.replace(marker, helper + marker, 1)
    else:
        # insert before final class brace as a safe fallback
        pos = m.rfind('}')
        if pos < 0:
            raise SystemExit('MainActivity brace missing')
        m = m[:pos] + helper + m[pos:]

# Trigger exact-alarm permission from first-run startup, but do not duplicate on later hardening runs.
if 'ensureExactAlarmAccess();' not in m.split('void ensureExactAlarmAccess()', 1)[0]:
    if 'requestNeededPermissions();' in m:
        m = m.replace('requestNeededPermissions();', 'requestNeededPermissions();\n        ensureExactAlarmAccess();', 1)

m = m.replace('نسخه ۴٫۰', 'نسخه ۴٫۱').replace('نسخه ۴.۰', 'نسخه ۴.۱')
main.write_text(m, encoding='utf-8')

# 4) Adaptive launcher icon without altering the premium artwork itself.
values = APP / 'src/main/res/values'
values.mkdir(parents=True, exist_ok=True)
(values / 'launcher_colors.xml').write_text(
    '<?xml version="1.0" encoding="utf-8"?>\n<resources><color name="launcher_bg">#FAF6EE</color></resources>\n',
    encoding='utf-8')
for d in ['mipmap-anydpi-v26']:
    p = APP / 'src/main/res' / d
    p.mkdir(parents=True, exist_ok=True)
    (p / 'ic_launcher.xml').write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/launcher_bg"/><foreground android:drawable="@drawable/ic_launcher"/></adaptive-icon>\n',
        encoding='utf-8')
    (p / 'ic_launcher_round.xml').write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/launcher_bg"/><foreground android:drawable="@drawable/ic_launcher"/></adaptive-icon>\n',
        encoding='utf-8')
# Keep legacy icon for Android < 26, add adaptive/round resources only where supported.

# 5) Compile-time assertions: fail the build if the hardening layer did not take effect.
assert 'versionCode 6' in gradle.read_text(encoding='utf-8')
assert "versionName '4.1.0'" in gradle.read_text(encoding='utf-8')
assert 'ensureExactAlarmAccess();' in main.read_text(encoding='utf-8')
assert 'android.intent.action.TIMEZONE_CHANGED' in manifest.read_text(encoding='utf-8')
assert 'allowBackup="false"' in manifest.read_text(encoding='utf-8')
print('Nadaye v4.1 post-overlay hardening applied successfully')
