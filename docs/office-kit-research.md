# Office Kit integration research

Reviewed 4 October 2026 (IST). This is pre-event preparation. **Actual Office Kit
pairing, transfer, app compatibility and HackTracker recognition remain unverified.**

The practical route is to produce ordinary local files in Mini Film, then use the
supported iQOO system bridge to move them to the laptop. No public, documented
Office Kit Android SDK contract was found in the official sources reviewed.
This does not establish that a private or partner SDK does not exist.

## Event requirement

The [official guide](https://iqoo.reskilll.com/guide) requires iQOO as the build
and demo surface, describes Office Kit screen mirroring, clipboard, file transfer
and remote control, and assigns bridge usage 10% of the score through HackTracker
counts/durations. It says loaners arrive paired and pairing instructions are
provided. The reviewed rules do not prescribe an embedded Office Kit SDK in the
submitted APK. Treating the supported system bridge as the integration route is
an inference from those rules, pending organizer validation at the teach-in.

The [finale homepage](https://iqoo.reskilll.com/) schedules the 9–11 October
finale's teach-in at Friday's 19:00 kickoff. Its Red Light periods allow the iQOO
phone with Office Kit as the route to the laptop; Green Light permits both
devices. It identifies an OriginOS 6 iQOO loaner and Windows 10+/macOS 10.14.6+
desktop clients. The guide's Saturday wording refers to the city battle format.
Preparation code remains subject to the separately documented event-window rule.

## Public interfaces and boundaries

| Source | Documented capability | Consequence for Mini Film |
| --- | --- | --- |
| [Official Office Kit product/download site](https://pc.vivoglobal.com/) | Supports vivo/iQOO phones with Windows, Mac and iPad clients; mirroring, file sharing, clipboard and remote PC. Some features have additional requirements: Super Clipboard needs macOS 11+, remote PC lists Windows 10+/macOS 10.14.6+ and supported flagship phones. | Nothing Phone development cannot establish vendor bridge support. Confirm the exact loaner's supported features; avoid assuming every advertised feature is present. |
| [Official iQOO 15 product documentation](https://www.iqoo.com/my/products/iqoo-15) | Mirroring drag-and-drop explicitly supports native vivo Albums/File Manager, excluding Google apps. Cross-device file transfer uses an iQOO account and requires desktop Office Kit 6.0.0+ and OriginOS 6.0+. | Save media where native system apps can find it. This model-specific page is not proof of the actual loaner's model or behavior. Account-based transfer can work across different networks, so do not claim an entirely offline transport. |
| [vivo developer catalog](https://developers.vivo.com/product/) | Lists separate application, AI, media, system and casting services. | Search did not yield an Office Kit Android artifact/version/license, public transfer API, broadcast, provider or deep-link contract. No vendor package/activity is hardcoded. |
| [Device-side intent-sharing specification](https://developers.vivo.com/doc/d/97ca17a6049a4b57a66bdbb1b52b5582) | ContentProvider or device SDK shares intent/behavior/content with vivo's intent framework. | This is a different service, not documented Office Kit laptop file transfer. Do not substitute its provider for an Office Kit API. |
| [CastKit](https://developers.vivo.com/product/d/castKit) | Car/TV large-screen integration, with Jovi InCar SDK examples. | It does not establish an Office Kit desktop export route or Nothing Phone compatibility. |

No SDK was downloaded, account registered, partner form submitted or message sent.
Direct HTTP reads of several developer pages returned JavaScript shells; the
substantive capability descriptions above came from indexed official pages.
The public Office Kit website bundle was inspected in memory for documentation
links; its internal website endpoints are not treated as Android APIs.

## What can be prepared on Nothing Phone now

Source inspection confirms the current prototype saves exported MP4 through
MediaStore to `Movies/MiniFilm`; its reel and edit-list share buttons use
`ACTION_SEND`, a content URI, MIME type, temporary read permission and the system
chooser. See [MainActivity](../prototype/phone-director/app/src/main/java/dev/minifilm/director/MainActivity.java)
and [ReelExporter](../prototype/phone-director/app/src/main/java/dev/minifilm/director/ReelExporter.java).
Those are standard Android exports, not verified Office Kit transfers.

[Android sharing documentation](https://developer.android.com/develop/ui/compose/sharing/send)
supports that generic intent route; [secure file sharing](https://developer.android.com/training/secure-file-sharing)
documents FileProvider content URIs and temporary access. A chooser opening or a
target being selected does not prove the destination received the bytes.

A useful next preparation is **Save editable cut list** using
`ACTION_CREATE_DOCUMENT`, so the creator can place JSON beside the MP4 in a local
folder accessible to the vendor File Manager. A shoot pack can additionally
contain a reviewed SRT, source names, relative paths and checksums. These additions
are proposed, not implemented by this research. The
[Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)
lets the user select a destination without broad storage permission, but includes
cloud providers. Use a deliberately selected local destination for the bench
test; private uploads require explicit authorization. The exporting app's lack
of INTERNET permission does not constrain a receiving app's transport.

## Hardware acceptance gate

1. At check-in, record the loaner model, OriginOS and Office Kit versions, laptop
   client version and features supplied by organizers. Keep hardware identifiers
   and account details out of Git. Follow supplied pairing instructions.
2. Use a generated, non-private MP4 and editable JSON saved in a user-visible
   folder. Transfer both through the actual supported Office Kit File Manager or
   Albums flow. Record what the user does and any access/format failure.
3. Verify received byte counts/checksums, video playback and usable cut-list
   contents on the laptop. Then test a disconnected/reconnected transfer without
   altering firmware, accounts or device binding.
4. Validate phone-led operation during the event's allowed mode and ask the
   on-site mentor to confirm HackTracker recognition. A generic share, USB copy
   or manually asserted usage duration is not Office Kit scoring evidence.

## Remaining access limits

The exact loaner, pairing transport, account requirements for the event setup,
sharesheet target, transfer completion signal, compatibility with app-generated
JSON, supported-model matrix and organizer reuse approval are unresolved. The
interactive product guide/model picker was not fully audited; marketing for
newer OriginOS features must not be applied to an OriginOS 6 loaner by assumption.
No Office Kit credentials or proprietary integration terms were available, so
there is no SDK version/license to pin and no API execution claim.

This research adds only this Markdown document. No SDK, model, desktop installer,
media asset or device installation was retained; no hardware mutation or private
transfer occurred. A successful real iQOO-to-laptop transfer remains necessary
before marking Office Kit complete in `docs/status.md`.

## Follow-up development-phone check

The app now implements Save editable cut list to Files using standard Android
ACTION_CREATE_DOCUMENT. Root exercised the normal picker in local Downloads:
1,512-byte synthetic JSON saved successfully and hash matched the original
export. This verifies a file-visible preparation path, not the vendor bridge
or iQOO/HackTracker result.
