# Security Patrol Camera — v0.1.8

A simple offline-first Android app for security guards who need to take many stamped photographs quickly and send a patrol batch to WhatsApp.

## Three screens only

**1. Camera**
- Launches straight to the camera. Start or end a shift with the company/site saved in Settings.
- Fast consecutive shots; the preview never closes between pictures. Hardware volume keys can also trigger a photo.
- UK date/time (GMT/BST), company name, editable site label and GPS (when enabled and available) printed on compressed WhatsApp copies.
- Compact black-on-white TAKE PHOTO shutter, Flash Off/On/Auto, torch, and a small Next Patrol control.
- Optional in-app Camera Lock hides other controls. Hold TAKE PHOTO for two seconds to unlock. This is not the Android device lock.
- Automatic hourly grouping can be switched on/off in Settings; manual Next Patrol still works.

**2. Photos**
- Browse saved shifts, then select one hourly patrol or intentionally choose the whole shift.
- Unsent photos are preselected. Select All, Unsent or Clear; tap a thumbnail to inspect the stamped copy.
- Share selected photographs to WhatsApp, WhatsApp Business or another app. Choose the actual WhatsApp recipient/group and confirm Send in WhatsApp.
- If the app does not know whether a batch was sent, Confirm Sent lets the officer manually confirm; no automatic delivery claim.
- Delete selected unwanted photographs only after explicit confirmation. The app removes their small/base/original copies and index entry.
- An individual photo's **site label** can still be corrected from its larger preview without changing its timestamp or recorded GPS.

**3. Settings**
- Choose or save security company and site, including selecting from previously used companies/sites.
- GPS enabled/disabled; UK date/time stamps are always kept; automatic hourly patrol grouping toggle.
- Choose Small, Balanced (default) or Higher quality for **future compressed copies**.
- Inspect small-copy and large-original storage usage.
- Deliberately export any saved shift to a ZIP, with a text report and its stamped photographs (optionally available originals). Export is not encrypted and cannot yet be restored/imported into the app.
- Delete verified large originals from a **completed shift** without deleting the small copies. Confirmation required.

## Data, backwards compatibility and backup

The existing `uk.org.securitypatrol` application ID and `PatrolStore` JSON format are retained. v0.1.8 does **not** wipe or migrate the user's saved shift/photo data. Fields for historic patrol notes, incidents and checkpoints still exist in records, but their extra buttons are intentionally removed from the streamlined interface.

The previous working v0.1.7 code is archived separately at `archive/v0.1.7-working`, so we have a rollback baseline.

**Important:** An Android upgrade preserves the app's private photographs only if it is signed with the same key. Never uninstall a previous debug build unless you have deliberately exported the data you need. A ZIP export can be reviewed outside the app, but ZIP import is not implemented.

## Open and test

1. Open Android Studio → **Get from VCS**, enter `https://github.com/rbabbit/securityapp.git`, or **Git → Pull** on an existing clone. Use the `main` branch.
2. Sync Android Gradle and connect your Android device; choose **Run ▶**.
3. In Settings, enter the current security company and site. Return to Camera and start the shift.
4. Take 10–20 rapid photos. Switch to Photos, select All or the unsent set, and share to a test WhatsApp group.
5. Check the timestamp and company/site/GPS on the photographs, Camera Lock, hourly patrol grouping, GPS off and compression choices.
6. Export a test shift ZIP to a local folder and inspect its contents before deleting any originals.

An automated GitHub Actions build produces a debug APK at **Actions → Build Android APK → Artifacts** after the Gradle build succeeds. Successful compilation does not replace phone-level testing.

## Implementation

Kotlin, CameraX, Android LocationManager, app-private files, JPEG processing queue, `FileProvider`, `ACTION_SEND_MULTIPLE` to WhatsApp, and Android's system document picker for ZIP exports. No account or cloud backend is required.

**WhatsApp limitation:** Personal WhatsApp groups cannot be reliably enumerated/targeted by an ordinary third-party app. WhatsApp's own recipient selection remains the final step.

Employer policies must permit photographs, locations, storage and any external sharing. ZIP files are not encrypted, and compressed pictures may not retain all evidence details.
