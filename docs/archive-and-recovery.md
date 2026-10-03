# FocusPilot archive and recovery

On 4 October 2026 the user authorized a full backup of FocusPilot and removal of
its data from this working folder, followed by the Mini Film Director research
pivot. The GitHub FocusPilot repository is retained at
https://github.com/sanjuhs/iqoo-focuspilot and is now marked archived/read-only.
Its published code and releases are retained, and the application dashboard was
not changed. The new private preparation repository is
https://github.com/sanjuhs/iqoo-film-director.

## Private full backup

The backup directory is a sibling of this workspace:

`/Users/sanju/Desktop/coding/hackathons/iqoo-focuspilot-research-archive-2026-10-04/`

It contains:

- `focuspilot-full.tar.zst`: complete workspace snapshot, including original `.git`,
  ignored files, uncommitted changes, `.env`, model weights, development dependencies,
  builds, recordings, evidence and original product documentation.
- `manifest.private.json`: original paths, types, permissions, timestamps, file
  SHA256 hashes and extended-attribute checksums.
- `verification.private.json`: streamed archive checks against every original
  regular file and symlink, all macOS extended attributes, plus a source-unchanged
  check before cleanup.
- `SHA256SUMS`: checksum of the complete compressed archive.

The archive directory is owner-only and the archive file is owner-readable/writable.
It contains credentials and private material. It is not encrypted and must remain
local; it must never be committed or published. The original symlinks are retained;
shared toolchains and files outside the old workspace are not copied into it.

The active `.env` is retained separately and verified byte-for-byte against the
archived source. A Git checkout alone cannot recover this private configuration.

## Verify without restoring

```sh
cd /Users/sanju/Desktop/coding/hackathons/iqoo-focuspilot-research-archive-2026-10-04
shasum -a 256 -c SHA256SUMS
zstd -t --long=30 focuspilot-full.tar.zst
```

## Restore into a separate private folder

Restore into a **new** empty destination, never over the current film-director
workspace. The full original logical contents occupy about 9.57 GB, in addition
to the compressed archive; check the 15 GB research budget and available disk
before restoring. Prefer selected files if a full restoration is unnecessary.

```sh
umask 077
mkdir /Users/sanju/Desktop/coding/hackathons/focuspilot-restored-2026-10-04
set -o pipefail
zstd -dc --long=30 /Users/sanju/Desktop/coding/hackathons/iqoo-focuspilot-research-archive-2026-10-04/focuspilot-full.tar.zst \
  | tar -xpf - -C /Users/sanju/Desktop/coding/hackathons/focuspilot-restored-2026-10-04
```

The restored `iqoo-hackathon/` subfolder includes the original Git history and
working-tree state. Private records and credentials remain private. Do not treat
its pre-event implementation as eligible event-written code.

The archive is the only complete local copy after cleanup. Preserve it. The new
project starts with independent Git history and must never push its replacement
tree to the FocusPilot GitHub remote.
