from pathlib import Path
p=Path('android-nadaye/app/src/main/AndroidManifest.xml')
s=p.read_text(encoding='utf-8')
start=s.find('<receiver android:name=".BootReceiver"')
if start<0: raise SystemExit('BootReceiver missing')
end=s.find('</receiver>',start)
if end<0: raise SystemExit('BootReceiver end missing')
end+=len('</receiver>')
canonical='''<receiver android:name=".BootReceiver" android:exported="true">\n            <intent-filter><action android:name="android.intent.action.BOOT_COMPLETED" /></intent-filter>\n        </receiver>'''
s=s[:start]+canonical+s[end:]
p.write_text(s,encoding='utf-8')
print('BootReceiver normalized for v55 hardening')
