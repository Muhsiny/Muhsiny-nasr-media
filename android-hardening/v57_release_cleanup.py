from pathlib import Path

# AdhanService: only one bundled offline Iranian fallback is required; other curated choices are remote/cacheable.
a=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/AdhanService.java')
t=a.read_text(encoding='utf-8')
t=t.replace('    static final int[] BUILTIN_RAW={R.raw.default_adhan,R.raw.adhan_beautiful,R.raw.adhan_azan};\n','')
a.write_text(t,encoding='utf-8')

# Public source should not contain legacy central publishing symbols after the old experiment is retired.
p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/MainActivity.java')
s=p.read_text(encoding='utf-8')
for bad in ['Central owner publishing','showCentralPublisher()','centralSyncAsync(false)']:
    assert bad not in s, bad
p.write_text(s,encoding='utf-8')

print('v5.7 release cleanup: dead adhan resources and stale central code removed')
