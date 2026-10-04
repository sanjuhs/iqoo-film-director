# Local reference-video inspection — 4 October 2026

Pre-event research prototype. `ReferenceAnalyzer` accepts only a user-selected
local `content:` or `file:` URI, rejects web URLs, and inspects video without
camera/microphone access, network calls or private-media uploads. A document
provider may require the user to download a cloud-backed item before inspection;
this analyzer itself has no remote fetch or upload path.

Public API: `ReferenceAnalyzer(Context)`, `analyze(Uri, Listener)`, `close()`.
The listener receives `onResult(String summary, long elapsedMs)` or
`onError(String message)` on Android's main thread. Work runs on one background
worker, one analysis at a time. Closing suppresses result callbacks and prevents
new jobs. An already-running platform decode can finish before cleanup; the
platform decoder does not offer a hard per-call cancellation deadline.

Videos over three minutes are rejected with a trim instruction. At most 24
uniformly spaced frames are decoded, bounded to 480×640 pixels with preserved
aspect. Each frame is recycled after measurement. A 32×32 pixel grid supplies
mean RGB, RGB histograms (16 bins/channel) and normalized adjacent-frame RGB
change. A histogram L1 distance/6 of at least 0.20 identifies an **approximate
change candidate**. Candidate count plus one is an approximate visual-beat
count. Non-candidate frame differences supply a frame-change proxy; this is
neither optical flow nor a measured motion trajectory. Lighting changes or
camera motion can falsely look like cuts; similar-color cuts and rapid scenes
between samples can be missed. Nearby decoder frames need not match requested
timestamps exactly.

The existing bundled ML Kit single-person pose detector, configured for CPU,
is applied to at most six sampled frames. A visible nose and both shoulders
with in-frame likelihood ≥0.65 count as a detected-person sample. The output
reports detections across successful pose samples, **not a unique-person count**,
crowd count or proof that no person occurs anywhere in the video. Failed or
time-limited pose samples are marked partial/unavailable. Individual pose tasks
have a five-second wait; a timed-out task retains its bitmap until completion
and prevents additional pose requests. The outer worker checks a 45-second
budget between bounded steps. No new weights or inference runtime are required.

The summary is at most 300 characters and states duration, sampled-frame count,
approximate change/beat count, mean RGB, frame-change proxy and pose sample
results. It explicitly labels sparse heuristics and excludes object/style
recognition. It does not infer a jacket, fabric detail, emotion, trend meaning,
creative intent, audio semantics or full-video continuity. File names and
reference metadata do not establish those properties. The root activity may
feed these factual observations and a user-supplied brief/style to the local
text planner; label that result **sampled observations + editable LLM draft**.
A language model's proposed story is not additional measured visual evidence.

## Official API references

[Android MediaMetadataRetriever](https://developer.android.com/reference/android/media/MediaMetadataRetriever)
documents metadata, scaled frame access, nearby timestamps and decoder cleanup.
[ML Kit Android pose detection](https://developers.google.com/ml-kit/vision/pose-detection/android)
documents the bundled detector and CPU configuration. API availability does not
verify useful trend understanding, camera integration or NPU execution.

## Tests and evidence state

`ReferenceAnalyzerTest` defines three real Android checks using only explicitly
synthetic fixtures:

1. `files/fixtures/jacket-speech.mp4`: flat-color video plus synthetic English
   speech must produce zero change candidates, zero detected-person samples,
   no visual jacket/fashion invention, and a bounded labelled summary.
2. The existing three-scene locally generated demo is exported and inspected;
   its two scene transitions must produce two approximate change candidates
   and no detected person. This tests the real decoder/heuristic path.
3. A web URI is rejected before analysis; the target app lacks INTERNET
   permission.

All three tests passed on the connected Phone (3a) in 4.883 seconds; the ignored runner receipt is `private/evidence/reference-test.log`. The synthetic three-scene reel yielded ten sampled frames and two approximate change candidates (1,326 ms). Flat synthetic speech video yielded nine sampled frames and zero candidates (1,734 ms). Both had zero detected-person samples out of six. Web-URI rejection passed without a fetch.
The cases test a negative fixture and obvious synthetic boundaries; they do
not establish accuracy on real trends, transitions, people or garments.
No private reference was accessed by this development work.
