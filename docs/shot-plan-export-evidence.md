# Editable plan context in local exports — 4 October 2026

Pre-event research under `prototype/`. The earlier assignment increment stored
explicit reviewed shot IDs, but an external reader had no current-plan lookup
for names or directions. This increment includes a bounded immutable current
plan snapshot in device and portable edit documents and adds readable portable
shoot notes. It does not infer visual coverage or approve AI directions.

## Behavior

Current IDs, order, titles, directions, captions, target durations and the saved
source label are preserved exactly at export dispatch. Copies do not rename or
regenerate IDs. Serialized plan order is one-based, matching portable cut order.
Creator-reviewed mapping resolves only by exact current ID; unknown/earlier IDs
stay unresolved rather than being matched by title or position. A source label
distinguishes a saved AI/template/manual plan without claiming creator approval.

Portable `shoot-notes.txt` describes current directions and selected-cut
assignments with source-relative times. The per-cut original `shotId` and device
`sourceUri` fields remain omitted from portable JSON; explicit current plan IDs
and reviewed mapping IDs are included. A current plan ID can equal a captured
take's original ID by design. Original source metadata remains in copied media;
this does not strip personal details embedded in the selected originals.

A package fingerprint includes current plan contents/order/source along with
cuts/title/look. Plan edits invalidate an older ready ZIP. Completion binds the
ZIP to its dispatch fingerprint rather than marking it current using later
state. Invalid snapshot input fails without truncating or rewriting creator
edits. Old export overloads can keep empty plan context for compatible callers.

## Verification

Final app/test build passed in 1s; test-only retention build passed in 793ms.
Normal installs passed without permission grants. Physical headless checks passed
**26/7.049s**: five new snapshot, four new export/ZIP, five coverage, two mapping,
six package, three subtitle-overlap and one metadata-only media checks. A targeted
retention rerun passed **1/0.165s** after the ordinary suite. Both original-source
SHA/size/mtime and saved preferences stayed intact.

Own fresh API36 ARM64 emulator interface checks passed **22/85.366s**: four new
package lifecycle methods plus coverage, assembly, workflow and capture recovery
regressions. The new checks verify seven plan fingerprint mutations, controlled
validation without rewriting edits, a gated real ZIP completion after plan edits
being removed as stale, and a stable real ZIP staying ready across recreation.
The elapsed suite time includes synthetic fixture creation and emulator/UI work;
it does not measure product latency or real creator usability. Host camera/audio
and snapshots were off; airplane mode was verified. Only this test AVD and its
matching registration were removed.

The six-argument real encoder published a readable **720×1280 H264/AAC** reel,
nominal1000ms and encoded1043ms. Host inspection found1.043356s and decoded a
frame with the synthetic title and caption visible. Caller plan, mapping, trim
and caption mutations after dispatch did not affect the exported snapshot.
A separate actual two-cut caption regression retained4064ms and both adjacent
caption shapes. These are synthetic fixtures, not real fashion/face footage.

Saved local artifacts (ignored by Git):

- `output/demo/synthetic-plan-context-reel.mp4`:28,994B,
  SHA256`8a374b379a081ec9bd2d09280bef050d1d20b9dc7fb96f9fc1b6b27df40463d8`.
- Its device-bound JSON:1,651B,
  SHA256`9f4927a54667ddb523245964a6cb2228f770270b5170879c575671e83ce20bb7`.
- `output/demo/synthetic-plan-context-edit.zip`:63,781B,
  SHA256`0f804169782e8db8dc372fde9974b77485e95fc872ed10847055ba20d4c56f8e`.
  Independently inspected four entries:project JSON, README, human notes and one
  deduplicated original. Two cuts total4000ms and preserve two current plan shots,
  captions/times and explicit assignments; unknown earlier IDs remain unresolved.
  Original SHA256`fb95e4027757071bc33f02562945f02510756a41dbfd31badc16eced398143f0`
  matched copied bytes. This two-cut ZIP is a separate fixture from the one-second
  MP4. Only the verified test-owned cache ZIP and marker were removed after copy.

Built/saved/independently read installed APK:52,838,059B,
SHA256`904c9c01e60283c8c05826f5463900cb05c89c5fa0ec337615c65def1d75e869`.
Notices31,635B/source-identicalSHA256`02797d896355991c613f9f2081f2687b4328ea63362cc355f8baa9955f89bc68`.
Separate Qwen/Whisper weights stay excluded from Git/APK; bundled MLKit SDK
assets remain. No Internet permission. Owned gallery metadata:25 rows/0pending/
7,954,004B. Normal Main launch requested behind secure keyguard; final read-only
verification confirmed camera/mic denied. No unlock, private footage upload or
permission grant occurred.

Qualified final adjacent storage is **9,295,612,529B**, below10GB aim and15GB cap;
maximum observed temporary test sample plus registration **10,514,539,385B**, above
10GB aim/below15GB cap. Existing missing-archive/cache/oat/provider and full-machine
inventory limitations persist. No new model/SDK/JDK/system-image downloads here.

Readable notes and local ZIP creation do not prove desktop editor import, Office
Kit transfer, source synchronization, real creator usability or hackathon
acceptance. Attended filming/AirPods, useful learned direction and semantic
review, iQOO/NPU, event-code eligibility and accepted submission remain open.
