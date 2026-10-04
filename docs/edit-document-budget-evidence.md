# Keep exported cut lists within their recovery and Files bound

4 October 2026; pre-event research under `prototype/`.

The exporter previously accepted up to12 cuts with500 subtitle rows of1000
characters per cut, and serialized the complete source-time words, including
those outside the selected range. That document could exceed1,048,576 bytes,
which both startup recovery and Save cut list to Files already rejected. This
is a source-backed contract mismatch; no private creator loss is claimed.

The exporter now allocates its real UUID, freezes the exact pretty UTF-8 edit
JSON from the isolated validated cuts and immutable plan, and checks its size
before creating a journal, temporary file, encoder or gallery row. Publishing
writes those exact accepted bytes. A shared1,048,576-byte constant preserves the
existing recovery/Files bounds. Overflow explains how to reduce subtitle text/
segments; all words, off-cut rows, originals and prior output remain unchanged.
No truncation, new schema, model, codec or numerical subtitle policy was added.

Three new metadata-only device checks cover exact-limit acceptance/one-byte
refusal, pretty versus compact size and multibyte UTF-8; normal real serializer
with unchanged source/timeline mapping and independent frozen bytes; and public
export refusal for two previously allowed large off-cut lists. The latter uses
an unresolvable labelled synthetic URI and verifies no progress/encoder/journal/
temporary/output/gallery mutation, no changed alias/word/time/source/range or
preferences. These checks do not decode a source or establish actual encoding.


Offline build1s (11 executed/51 cached), normal app/test installs without grants.
Fresh own API36 UI14/26.286s passed (revised NewReel4 + workflow10). Physical
focused6/2.885s passed: new budget3, actual encoded plan1, published-ready recovery1
and copier byte-limit refusal1. Actual encoded synthetic plan output1043ms at
720×1280 decoded a frame and preserved frozen plan/assignments, original SHA and
preferences; its completed pair remains local with only its own journal detached.
Gallery metadata1/0.301s now31owned/0pending/8,563,448B. No runtime failures.

Built/saved/independently read installedAPK52838059B SHA256
45b96786e2871a4b65af8b4c3a56094fc92bda345d4acb684dbb34d91e09d76f. Source-identical notices,
separate Qwen/Whisper weights excluded/bundledMLKit/noInternet. Normal Main launch
requested behind secure physical keyguard; camera/microphone denied. Independent
final source review found no blocker. No new models/dependencies/SDK/JDK download,
private capture/upload, physical playback or aircraft action. Only own AVD/reg
removed after parent terminal; other AVD untouched. Qualified peak10900650701B/
finaladjacent9366797005B with raw/labelled synthetic walkthrough inside workspace,
external cover counted once and historic missing-archive/cache reserves/runtime
exclusions retained. Attended/AirPods/iQOO/NPU/OfficeKit/eligible acceptance remain
open; this does not establish positive filming or creator benefit.
