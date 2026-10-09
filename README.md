# Security Patrol Camera (Android)

An Android-first, offline-capable patrol photography app for security officers who need **fast successive photos**, an accurate UK date/time stamp, location details, smaller files, and quick sharing to workplace WhatsApp groups.

> **Project status:** Requirements/planning. No functioning Android app has been built or tested yet.

## Core workflow

1. Open straight to the ready camera; begin or resume a shift under a selected company and site (no loading screen between photos).
2. Take 10, 20, 50 or more consecutive pictures; each shot is queued for local saving and background processing without blocking the next shutter press.
3. Record the capture-time UK timestamp, available GPS coordinates/accuracy and a **manually editable place/site label**, e.g. `Job Centre: Jappi Lane, BD1232`.
4. Save compact, annotated copies in **private storage inside the app** for its patrol gallery and WhatsApp sharing. Capture originals may exist temporarily or be retained by user choice.
5. Review by shift, pick individual pictures or the entire unsent batch, and share during patrols or at the end.
6. Open WhatsApp with the prepared batch. Select the true recipient/group in WhatsApp and confirm Send; never falsely claim direct group addressing.
7. Revisit shift history and free space using **Delete Large Originals**, with safeguards to preserve the smaller copies and associated shift records.

## MVP requirements

### Fast camera
- CameraX native Kotlin implementation; low-latency capture prioritised.
- Preview remains open between photos; large shutter button.
- Volume button shutter, focus/zoom, torch and flash controls; correct rotation/orientation.
- Dedicated processing queue for stamping/compression; saving the original must not wait for the sharing copy.
- Test throughput and correctness under 10, 20, 50+ consecutive captures on real Android devices.

### Timestamps and GPS
- Record **actual shutter/capture time**, not later processing time.
- Display UK format `dd/MM/yyyy HH:mm:ss` using the `Europe/London` timezone (GMT/BST).
- Obtain location while camera is active; record latitude/longitude, estimated accuracy, and fix timestamp/age.
- Never fabricate coordinates or silently claim an old fix is current. When location is unavailable, photograph capture still works and the app labels the missing/poor fix honestly.
- User-configurable burned-in overlay: timestamp, GPS/accuracy, company/site/checkpoint; keep it legible without hiding critical image detail.
- Store the actual location fix, its age/accuracy and source as captured metadata; never substitute an address typed by a person for measured GPS coordinates.
- **Editable site/place name:** permit entries such as `Job Centre: Jappi Lane, BD1232` on top of coordinates and time. This label must be editable before or after a shot and retained per company/site; editing a caption should regenerate the compressed overlay, not mutate the original photo or timestamp.
- Offer saved site/place labels and optional custom checkpoint labels, with a quick edit action on the camera screen; GPS should never delay or prevent a photo.
- Store source image and capture metadata separately from compressed/annotated sharing copies.

### Employers, sites, and WhatsApp
- Multiple named company/site profiles, each with site/checkpoint options, preferred overlay and saved sharing preferences.
- Allow picking the relevant company/site when beginning a shift, and another profile when sending if necessary.
- **Important WhatsApp limitation:** Android apps cannot reliably enumerate a person's existing WhatsApp group list or guarantee delivery to a specifically chosen personal group. Do not pretend that a locally saved label is an actual synced WhatsApp conversation.
- Use Android `ACTION_SEND_MULTIPLE` and content URIs / `FileProvider` to share batches; prefer WhatsApp when installed, but let WhatsApp's recipient selection and final Send occur in WhatsApp.
- Evaluate Android Direct Share shortcuts *as a convenience only*, never as guaranteed exact routing.
- Validate the sharing workflow on real phones before investing heavily in the remaining UI.

### Shift records and batch management
- **Shift records are core v1:** Start Shift / End Shift, date and local start/end times, employer, site/place name, optional shift notes, chronological pictures, total photos, batches and manually confirmed sends.
- Resume an active shift after closing the app; open previous shifts by company/site/date without uploading anything.
- Support many photos per shift (at least 100 retained; sending one batch of 50 is a **test goal**, not a WhatsApp guarantee).
- Share selected photos, all ready photos, or all unconfirmed photos; allow multiple batches over one shift and prevent mixing employers' work accidentally.
- Clearly distinguish: captured → processing → ready → shared-to-WhatsApp flow initiated → manually confirmed sent.
- WhatsApp sharing does not provide authoritative delivery acknowledgements to our app. Never mark as *delivered* merely because WhatsApp opened.
- Keep a local history of batch selection and user send-confirmation, plus any failed/retry attempts; maintain original image order.
- Keep site and checkpoint labels editable; optional individual photo notes and timestamp-preserving captions.

### Local photo storage, compression and original-photo cleanup
- **Small copies stored inside the actual app**, in its private storage and browsable in the app's photo gallery and shift records (not automatically added to the normal phone gallery).
- Prepare legible compressed JPEGs in the background, using an adjustable output quality/size target; typical desired sharing size may be ~200–500 KB per picture, **not a guaranteed fixed size** and never at the cost of unreadable important details.
- After capturing, preserve the source image while making and checking the compressed copy; allow users to choose between `Keep original`, `Delete selected originals`, or `Delete large originals for this completed shift`.
- **Never delete the only good copy.** An original is eligible for deletion only once its smaller version was saved and verified as readable. Deletion must require the user's explicit action and confirmation in v1; no silent cleanup by default.
- Deleting the large originals must **not** remove compact gallery photos, labels, GPS records, timestamps, shift history or previous send flags.
- Show a storage summary: small photos, large originals and estimated space reclaimable; warn before storage becomes low.
- Explain that deleting originals is irreversible and compressed copies can omit detail useful as evidence. Allow exporting/backing up a shift before deletion where employer policy permits.
- Because app-private photos can be lost upon uninstall or device reset, provide a deliberate export/backup option and an uninstall warning.

### Reliability and privacy
- Function without connectivity; avoid servers, accounts and cloud uploads for MVP.
- Crash/restart recovery, duplicate prevention and checks that all shared files exist and are accessible.
- Private local app storage; optional PIN/biometrics; explicit safe backup/export instead of any automatic cloud syncing.
- Do not infer a physical address from raw coordinates when unreliable; user-entered labels are independent of GPS and clearly identified as entered by the user.
- No photos, site locations, actual group names, keys, credentials or personal details in this **public** GitHub repository.
- Photographing sites/people, GPS stamping and use of WhatsApp must follow employer instructions and applicable workplace/privacy policies.

## Implementation direction
- Android app in **Kotlin**; **CameraX** for photo capture; platform location APIs or Fused Location Provider for location; background work queue for image processing; Room/DataStore for metadata/settings; content-based sharing.
- First milestone is a tiny **camera + batch-to-WhatsApp proof of concept**. Verify on a real Android device: fast repeated photos, correct UK overlay/GPS, selecting 10–50 JPEGs in WhatsApp, and safe return/retry behaviour.
- Second milestone: **shift records, in-app compact photo gallery, original-file cleanup controls, editable site/place names alongside measured GPS**, company/site profiles, sent-confirmation queue, settings, offline resilience and polish.
- Build and test APKs before considering publication.

## Open decisions
- Which Android phone models/OS versions should be supported?
- Which overlay elements should show by default, and what line breaks/font size keep the stamp readable?
- How many images per WhatsApp batch remain reliable on target phones?
- What retention period and export options do security employers allow?
- Should PIN/biometric lock and shift-summary export be in v1 or a subsequent release?
