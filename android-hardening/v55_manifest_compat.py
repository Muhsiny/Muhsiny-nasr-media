from pathlib import Path
p=Path('android-nadaye/app/src/main/AndroidManifest.xml')
s=p.read_text(encoding='utf-8')
start=s.find('<receiver android:name=".BootReceiver"')
if start<0: raise SystemExit('BootReceiver missing')
end=s.find('</receiver>',start)
if end<0: raise SystemExit('BootReceiver end missing')
end+=len('</receiver>')
canonical='''<receiver android:name=".BootReceiver" android:exported="true" android:directBootAware="true">\n            <intent-filter>\n                <action android:name="android.intent.action.BOOT_COMPLETED" />\n                <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />\n                <action android:name="android.intent.action.TIME_SET" />\n                <action android:name="android.intent.action.TIMEZONE_CHANGED" />\n                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />\n            </intent-filter>\n        </receiver>'''
s=s[:start]+canonical+s[end:]
if 'android.permission.USE_EXACT_ALARM' not in s:
    s=s.replace('<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />','<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />\n    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />')
p.write_text(s,encoding='utf-8')
print('BootReceiver and exact-alarm manifest hardened')
