from pathlib import Path
import re
p=Path('android-nadaye/app/src/main/java/com/nadaye/beheshti/MainActivity.java')
s=p.read_text(encoding='utf-8')
s=re.sub(r'^\s*static final String CENTRAL_BASE=.*?;\s*\n','',s,flags=re.M)
for bad in ['CENTRAL_BASE','Central owner publishing','showCentralPublisher()','centralSyncAsync(false)','nadaye-beheshti-central-ziua8x','nadaye-beheshti-central-api.lovable.app']:
    assert bad not in s, bad
p.write_text(s,encoding='utf-8')
print('v5.8 legacy central publishing fully removed')
