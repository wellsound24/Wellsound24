from pathlib import Path
p=Path('well-assistant-pro-android/app/src/main/java/com/wellsound/assistantpro/MainActivity.java')
s=p.read_text()
old='byte[] req = encodeOsc("/meters", "s", "meters/1");'
new='byte[] req = encodeOsc("/meters", "string", "meters/1");'
if old in s:
    s=s.replace(old,new)
# Also correct the source patch so future rebuilds do not reintroduce the bug.
p.write_text(s)
q=Path('tools/patch_well_assistant_live_vu_autoconnect.py')
if q.exists():
    t=q.read_text()
    t=t.replace('encodeOsc("/meters", "s", "meters/1")','encodeOsc("/meters", "string", "meters/1")')
    q.write_text(t)
