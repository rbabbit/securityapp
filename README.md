# Security Patrol Camera (Android)

An Android-first, offline-capable patrol photography app for security officers who need **fast successive photos**, an accurate UK date/time stamp, location details, smaller files, and quick sharing to workplace WhatsApp groups.

> **Project status:** Requirements/planning. No functioning Android app has been built or tested yet.

## Core workflow

1. Launch directly into a ready-to-use camera and choose the current company/site profile.
2. Take many pictures in quick succession without returning to another screen between shots.
3. Save every original photo immediately, with a capture-time timestamp and location record.
4. Prepare compressed, visibly stamped sharing copies **asynchronously** so the shutter stays responsive.
5. Review, select, or delete sharing copies; share a batch mid-patrol, at the end, or both.
6. Hand off a prepared image batch to WhatsApp via the Android share system. Choose the actual recipient group and confirm sending in WhatsApp.
7. Keep the unsent queue and originals even if the app closes, the phone restarts, GPS becomes unavailable, or sharing fails.

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
- Store original images and capture metadata separately from compressed/annotated sharing copies.

### Employers, sites, and WhatsApp
- Multiple named company/site profiles, each with site/checkpoint options, preferred overlay and saved sharing preferences.
- Allow picking the relevant company/site when beginning a shift, and another profile when sending if necessary.
- **Important WhatsApp limitation:** Android apps cannot reliably enumerate a person's existing WhatsApp group list or guarantee delivery to a specifically chosen personal group. Do not pretend that a locally saved label is an actual synced WhatsApp conversation.
- Use Android `ACTION_SEND_MULTIPLE` and content URIs / `FileProvider` to share batches; prefer WhatsApp when installed, but let WhatsApp's recipient selection and final Send occur in WhatsApp.
- Evaluate Android Direct Share shortcuts *as a convenience only*, never as guaranteed exact routing.
- Validate the sharing workflow on real phones before investing heavily in the remaining UI.

### Shift/batch management
- Support many photos per shift (at least 100 retained; sending one batch of 50 is a **test goal**, not a WhatsApp guarantee).
- Share selected photos, all ready photos, or all unconfirmed photos; allow multiple batches over one shift.
- Clearly distinguish: captured → processing → ready → shared-to-WhatsApp flow initiated → manually confirmed sent.
- WhatsApp sharing does not provide authoritative delivery acknowledgements to our app. Never mark as *delivered* merely because WhatsApp opened.
- Retain originals and unconfirmed photos; user-controlled deletion and cleanup, low-storage warnings.
- Shift folders and chronological numbering; optional short notes/checkpoint tags.

### Reliability and privacy
- Function without connectivity; avoid servers, accounts and cloud uploads for MVP.
- Crash/restart recovery, duplicate prevention and checks that all shared files exist and are accessible.
- Private local app storage; optional PIN/biometrics; optional backup/export without exposing sensitive work data.
- No photos, site locations, actual group names, keys, credentials or personal details in this **public** GitHub repository.
- Photographing sites/people, GPS stamping and use of WhatsApp must follow employer instructions and applicable workplace/privacy policies.

## Implementation direction
- Android app in **Kotlin**; **CameraX** for photo capture; platform location APIs or Fused Location Provider for location; background work queue for image processing; Room/DataStore for metadata/settings; content-based sharing.
- First milestone is a tiny **camera + batch-to-WhatsApp proof of concept**. Verify on a real Android device: fast repeated photos, correct UK overlay/GPS, selecting 10–50 JPEGs in WhatsApp, and safe return/retry behaviour.
- Second milestone: company/site profiles, review gallery, sent-confirmation queue, settings, offline resilience and polish.
- Build and test APKs before considering publication.

## Open decisions
- Which Android phone models/OS versions should be supported?
- Which overlay elements are mandatory by default, and whether to keep GPS visible or in metadata only?
- How many images per WhatsApp batch remain reliable on target phones?
- What retention period and export options do security employers allow?
- Should checkpoint labels, shift summary export and PIN/biometric lock be in v1 or later?
