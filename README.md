# Security Patrol Camera — v0.1.11 (experimental title helper)

## NEW: WhatsApp Chat Title Helper — private test only

**Build:** test/whatsapp-title-helper-v0111 — not merged into stable main.

- Optional Android Accessibility Service, declared for **com.whatsapp** and **com.whatsapp.w4b** only. It is OFF until explicit in-app consent and explicit Android Accessibility enablement.
- When you choose the regular WhatsApp or WhatsApp Business sharing option, the test helper is armed for **120 seconds maximum**. It checks only exact WhatsApp chat title view IDs (such as `conversation_contact_name`), never message bodies or chat lists.
- A detected label is a **possible chat name, not proof of which group received the photos**. It may be a person's name, or the id may no longer exist in recent WhatsApp versions. You must confirm it yourself from Settings → WhatsApp group helper (TEST).
- Only the most recent candidate and the manually confirmed label are saved on your phone, in private app preferences. There is no server or upload, no automated WhatsApp UI actions, no sending, no bypass of permissions, and **no ability to reopen the group automatically**.
- The watch stops after 2 minutes or when you return to the Photos screen. You can delete the stored names and disable future observation within Settings; turn off the service in Android Accessibility settings to revoke permission completely.
- The existing WhatsApp image-sharing process is unchanged.
- This is an Android Studio/debug experiment; Android accessibility privacy disclosure and Google Play's separate Accessibility API declaration/approval rules apply if you ever consider publishing it.

### Test on your own phone
1. Install the debug build from this **test branch** (do not just pull main). Do **not uninstall** an app with important privately stored photos.
2. In Settings → WhatsApp group helper (TEST) → WhatsApp helper options, tap **Enable helper / Android permission** and accept the disclosure.
3. In Android Accessibility settings, explicitly enable **Security Patrol: WhatsApp Title Helper (TEST)**; on some sideloaded Android devices enabling restricted settings requires additional approval.
4. Return to the Patrol Camera and take or select test photos. Choose **WhatsApp**, not Other apps, from the photo-sharing popup.
5. Select your **Pizza group**, review the photo recipient, and send test images if appropriate; if WhatsApp never displays its actual chat header during sharing, visit the group's conversation briefly (within 2 minutes).
6. Return to Patrol Photos → Settings → WhatsApp helper options. Check **Possible chat (NOT confirmed)** and confirm only if the displayed name is truly Pizza.
7. If no name appears, the service or WhatsApp may not expose the expected title field. Send a screenshot of the test helper status; **do not send private WhatsApp messages**.
8. When done, **Forget name and turn off helper** in the app and disable its service in Android Accessibility.

### Privacy & practicality
- Enabling Accessibility is a powerful system permission. This code deliberately filters to WhatsApp title elements only, and does not enumerate chat lists, notifications, message contents, or contacts. It does not call performAction, inject touches, or intercept Send.
- A chat title alone is insufficient to identify a group securely or direct a future share. Duplicate group names exist, and WhatsApp does not officially promise stable identifiers to ordinary share-intent clients.
- Test with an innocuous personal group and harmless sample photos, not work-sensitive patrol content.
- The stable v0.1.10 code remains on main.



## v0.1.10 — cleaner sharing popup

- Removes the unnecessary “select group there” text from the image-sharing popup. It now offers only **WhatsApp**, **WhatsApp Business**, and **Other apps** under a short **Send N photos** title.
- The actual WhatsApp recipient/group is selected within WhatsApp. **Security Patrol does not know or save which group was selected**, and does not automatically send to a remembered group.
- Preserves the existing multi-image intent, photo attachments, FileProvider permissions, and manual sent-confirmation flow. No changes to the photo database, layout outside this popup, or company/site/GPS stamps.



## New in v0.1.9 — approved camera mockup implemented

- The camera preview now fills the screen behind a subtle dark translucent top/bottom overlay (no giant white controls covering the viewfinder).
- Top-left **Settings gear** icon. Top-right small **Flash** and **Torch** icons with their Off / On / Auto state beneath.
- The security **company, site and GPS** appear neatly over the preview.
- A small central **Start Shift / Next Patrol** pill sits just above the bottom actions; tapping it starts the shift or moves to the next round. To **End Shift**, open Settings and confirm.
- The only two main actions on the home screen are **TAKE PHOTO** (white with a camera icon) and **SEND PHOTOS** (dark with a send icon) **side by side on one line**.
- Both bottom buttons have **at least 30dp of inactive horizontal space at the screen edges** plus 14dp between them. Touching the margins does not activate either button, reducing accidental presses while holding the phone.
- **SEND PHOTOS** opens the existing gallery with unsent photos preselected; you choose the recipient and confirm the final Send inside WhatsApp, so the app never sends accidentally.
- **Camera Lock** remains available under Settings: while active only TAKE PHOTO is visible. Hold TAKE PHOTO for two seconds to unlock.
- Retains the proven CameraX fast shutter, UK photo stamps, GPS accuracy, compression, hourly rounds, local app storage, shutter sound, WhatsApp batches, and backup/export.
- The previous main branch is backed up as `archive/v0.1.8-three-screens`.
- The automated build verifies compilation, but real-device layout and rapid-photo handling must still be tested before relying on the redesigned UI at work.

**Safety:** Uninstalling the app deletes private photos and shifts. Update using the **same signing key**. If Android refuses to install an update, export anything important before making changes; ZIP import into the app is not yet implemented.

---

## v0.1.8 functionality

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
