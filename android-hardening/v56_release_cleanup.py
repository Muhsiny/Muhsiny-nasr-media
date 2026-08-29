from pathlib import Path

p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/AdhanService.java')
s=p.read_text(encoding='utf-8')
s=s.replace('static final int[] BUILTIN_RAW={R.raw.default_adhan,R.raw.adhan_beautiful,R.raw.adhan_azan};','static final int[] BUILTIN_RAW={R.raw.default_adhan};')
assert 'R.raw.adhan_beautiful' not in s
assert 'R.raw.adhan_azan' not in s
assert 'static final int[] BUILTIN_RAW={R.raw.default_adhan};' in s
p.write_text(s,encoding='utf-8')
print('v5.6 release cleanup: removed stale raw adhan references')
