# Security Patrol Camera (Android)

An Android-first, offline-capable patrol photography app for security officers who need **fast successive photos**, an accurate UK date/time stamp, location details, smaller files, and quick sharing to workplace WhatsApp groups.

> **Project status:** Android Studio Kotlin/CameraX **v0.1.2 test version source code committed**. The code is an initial proof of concept, **not a verified APK or production-tested app**. Build results, device speed and WhatsApp behaviour still need real testing.

1. In Android Studio, click **Get from VCS** (or **File → New → Project from Version Control**).
2. Paste `https://github.com/rbabbit/securityapp.git`, choose a local folder, and click **Clone**.
3. Open the cloned project; install the requested Android SDK 36 when prompted.
4. If sync complains that `gradle-wrapper.jar` is missing, open Android Studio's **Terminal** at the project root and run `.\\gradlew.bat help`. The script securely fetches and verifies the pinned Gradle wrapper binary, then Gradle 8.13. Click **Sync Project with Gradle Files**.
5. Connect an Android phone with USB debugging enabled, choose it in the device menu and press the green **Run ▶** button.
6. Grant Camera permission and (optionally) Location permission, start a shift, and try repeated pictures and a small WhatsApp batch.

This repo **is already an Android Studio project**. Do **not** make a second Empty Activity project inside it.

## v0.1.2 — hourly patrols + one-tap selection

- **Shift → Patrol → Photos:** one employer/site shift contains separate numbered patrol rounds, each with its own timestamps, photos and WhatsApp sharing selection. A shift can contain as many rounds as needed.
- **Next Patrol** on the camera: starts the next group deliberately; it does not interrupt the live camera preview.
- **Optional automatic hourly rounds:** when creating a shift, enable "Automatically group hourly patrols" (on by default). The next photo begins a new round only after at least **50 minutes since the first photo of the current round** and **15 minutes without a photo**. A patrol lasting across a clock-hour boundary is not cut in half. Tap Next Patrol to split earlier.
- The photo gallery has a **patrol dropdown**, normally opening the latest round. You can explicitly choose **All patrols (whole shift)** when needed.
- **Automatic selection of unsent photos**, plus **Select all**, **Unsent only**, and **Clear** buttons; never auto-send photos. Selections are scoped to the displayed round (or to the current shift only when viewing All patrols), never across employers.
- **Send selected** sends your ticked photos; **Send unsent** sends all ready, unconfirmed photos in the chosen round without any ticking. The camera's **Send this patrol** shortcut works the same way.
- If selected photos were already marked sent, a confirmation asks whether to share them again. WhatsApp's own group selection and final Send remain unchanged.
- **Backward compatibility:** older v0.1.1 shift/photo records are assigned to a legacy first patrol in memory, preserving their original capture times, private files, place names and manually confirmed send flags. Data is saved in format v2 when next updated.
- **Important test-device warning:** install upgrades only with the same app signing key. Don't uninstall v0.1.1 to install v0.1.2 unless you are willing to lose locally stored test photos, because export/backup is not built yet.

## Small test fixes in v0.1.1

- Camera and shift/gallery controls now apply Android system-bar insets, so bottom buttons stay above the navigation bar, including on Android 15+ phones.
- The photo-sharing popup now actually shows **WhatsApp**, **WhatsApp Business** and **Other apps**; the previous dialog mistakenly combined message text and a list of options.
- Short Android shutter-click sound plays immediately when the shutter is pressed, including the hardware volume-button shortcut. Its loudness depends on the phone's sound settings.
- No changes to local shift records, photo storage, compression or application ID. **An update preserves app-private photos and records only if both APKs use the same signing key.** GitHub Actions debug builds can use different debug keys between runs, so Android may refuse to install the new APK over the old one. **Do not uninstall an old test build before saving any photographs you want to keep:** uninstalling removes this app's private data. Builds from the same local Android Studio debug keystore can normally update one another.

## Features in v0.1 source

- Native CameraX preview stays open, low-latency repeated shutter with on-screen or phone volume button.
- On-device company/site shift start and end; shift history and chronological photo gallery.
- Optional current GPS fix (with actual accuracy where available) and precise UK capture time; editable site label, including per-photo restamping from a small un-stamped base.
- Each large original is stored privately inside the app; small compressed JPEG and re-stampable base are also stored privately.
- Bulk handoff of selected or unconfirmed photos to WhatsApp, WhatsApp Business or Android Sharesheet. **You must choose the actual WhatsApp group and press Send in WhatsApp.** No automatic retrieval of a personal WhatsApp groups list.
- Explicit manual confirmation after share handoff; no fabricated delivery status.
- Per-shift **Delete LARGE originals** action only when validated compressed base and share copies exist; warns that originals are irrecoverable.

## Current limitations and next work

- **v0.1.2 changes require a new build and real-device tests.** GitHub Actions compilation is a helpful check, but real-phone testing is still essential.
- Large batches (20–50) and fast consecutive photos need performance/device testing.
- The app uses a simple private JSON index; a hardened production version should use Room plus persistent Android WorkManager processing and recovery for interrupted compression.
- The app has **no exported backup** yet: uninstalling the app or resetting the device can lose all local photos and shift records. Don't use as your only evidence archive.
- No automatic group extraction: WhatsApp's existing contacts/groups are only selectable in WhatsApp's own UI. Shortcut appearance varies.
- PIN/biometric lock, a full company/site profile picker, explicit torch control, full-original evidence export, and per-shift export are follow-up enhancements.
- Never store real site photos, private GPS data or passwords in this public GitHub repository.

## Build/checking

A GitHub Actions workflow attempts `:app:assembleDebug` and uploads the debug APK as an artifact when builds succeed. It is a useful build check, not a substitute for real phone testing.

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
