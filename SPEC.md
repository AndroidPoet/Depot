# Depot — spec

Working name. A Compose Multiplatform client for the official F-Droid repository, for Android and desktop
(macOS, Windows, Linux). It browses, searches and installs free software. On Android it installs onto the phone
it runs on. On desktop it installs onto a phone connected through `adb`.

Not in scope for 1.0: extra repositories, mirrors, index diffs, background update checks, auto-update,
privileged (silent) install, downloading source tarballs, localisation of the UI, iOS.

## 1. Repository facts (probed 2026-09-30)

- Base address `https://f-droid.org/repo`.
- `entry.jar` (about 2.6 KB): a signed JAR holding `entry.json`. One signer, SHA256withRSA, 2048-bit key, no
  authenticated attributes. `java.util.jar.JarFile` verifies it on JDK 17 and 21. (Only the certificate's own
  self-signature is SHA-1, which does not matter because the certificate is pinned by fingerprint.)
- `entry.json`: `timestamp`, `version`, `maxAge`, `index { name, sha256, size, numPackages }`, `diffs`.
- `index-v2.json`: about 62 MB (19 MB gzipped), 4,486 packages. Shape: `repo`, `packages[pkg] { metadata, versions }`.
  Text fields are maps of locale to string. 388 packages have no icon. English text for the whole repo is under 5 MB.
  Every file name in the index (`file.name`, icon `name`) starts with `/`.
- Signing certificate SHA-256: `43238d512c1e5eb2d6569f4a3afbf5523418b82e0a3ed1552770abb9a9c9ccab`.

## 2. Trust chain

The entry, the index and every APK are tied back to the pinned certificate fingerprint. Icons are display-only:
they are fetched over HTTPS from the pinned host and are not hash-checked. The on-disk catalogue cache lives in
the app's private data directory and is trusted as written.

1. **Entry.** `entry.jar` is capped at 64 KB. It is opened with `JarFile` in verifying mode and `entry.json` is
   read to the end. It must then have exactly one code signer, and the SHA-256 of that signer's leaf certificate
   (DER) must equal the pinned fingerprint. No signer, more than one signer, a digest error or any parse problem
   fails closed.
2. **Rollback.** An entry whose `timestamp` is lower than the stored catalogue's is rejected. An equal timestamp
   means "already up to date" and downloads nothing.
3. **Index.** The decoded (not gzipped) index bytes are streamed to disk while hashing. The download aborts as
   soon as it exceeds the entry's `size`. SHA-256 and size must equal the entry's values before anything is parsed
   into the live catalogue. On mismatch the old catalogue stays.
4. **Names from the index.** A repository URL is the base address plus the name, by string concatenation. A name
   is accepted only if it starts with `/` and contains no `..`, backslash, `%`, `?`, `#`, `:` or whitespace.
   A version or icon with a bad name is dropped. A `sha256` must be 64 hex digits or the version is dropped.
5. **APK.** The local file name is `<sha256>.apk`, never derived from the index name. The download aborts when it
   exceeds the index size. SHA-256 and size must equal the index values before the file reaches an installer.
   The file is deleted after every install attempt, successful or not.
6. **APK identity.** After the hash check the APK itself is opened. Its signature must be valid. The package name
   in its manifest must equal the package being installed, and its version code must equal the catalogue's. When
   the catalogue names a signer, the APK must have exactly one signing certificate and its SHA-256 must equal it.
   Any mismatch discards the download. This runs on both platforms before an installer sees the file.
7. All traffic is HTTPS. No redirect to plain HTTP is followed.
8. **Desktop storage.** The data and cache directories are per-user (`Application Support` and `Caches` on macOS,
   `%APPDATA%` and `%LOCALAPPDATA%` on Windows, XDG directories on Linux) and are created owner-only where the
   file system supports it. Nothing is downloaded into a shared temp directory. `adb` is only taken from absolute
   directories.

## 3. Catalogue model

Kept in memory and cached on disk as one JSON file, written atomically (temp file, then rename).

`CatalogApp`: packageName, name, summary, description, iconUrl?, categories, license, authorName?, sourceCode?,
webSite?, added, lastUpdated, preferredSigner?, versions (newest first).

`AppVersion`: versionName, versionCode, apkPath, sha256, size, minSdk, targetSdk, maxSdk?, abis (empty =
universal), added, antiFeatures, beta (true when the version has any release channel), signer? (first entry of
`manifest.signer.sha256`).

`DeviceProfile`: sdk, abis in the device's preference order, minTargetSdk (the lowest `targetSdk` the device
will install: 23 on Android 14, 24 on Android 15 and later, 0 below that or when the installer can bypass it).

`InstalledApp`: versionCode, signer? (unknown on desktop: `adb` has no cheap way to read it).

Rules:
- **Locale pick** (text and icons): `en-US`, else any key starting with `en`, else the first entry. A missing map
  gives an empty string or no icon.
- **Name fallback:** an app with no name uses its package name.
- **Runs on:** `minSdk <= sdk`, `maxSdk` absent or `>= sdk`, `targetSdk >= minTargetSdk`, and `abis` empty or
  intersecting the device's ABIs. With no known device every version runs.
- **Can replace:** when the installed signer is known, only versions with the same signer.
- **Suggested version:** among versions that run and can replace: for a fresh install prefer those signed by
  `preferredSigner` when any exist; then take the highest non-beta `versionCode`, or the highest beta if there is
  no stable one. Among versions sharing that winner's `versionName` (ABI splits of one release), take the one
  whose ABI comes first in the device's preference order; a universal APK counts as first.
  No candidate means the app is shown as "Not compatible" and cannot be installed.
- **Update available:** the app is installed and its `versionCode` is lower than the suggested version's.

## 4. Browsing

- **Search** matches case-insensitively on name, summary and package name. Results where the name starts with the
  query come first, then name contains, then the rest; ties keep the current sort.
- **Category filter:** one category or all. Categories are listed with their app counts, alphabetically.
- **Sort:** recently updated (default), newest, name.
- **Views:** All apps, Installed, Updates. Installed and Updates need a target device.
- **Detail:** icon, name, author, summary, description, suggested version, size, licence, anti-features (named
  plainly, never hidden), links to source and website, and the version list.

## 5. Target device

One abstraction, two implementations.

- **Android:** the phone itself. Installed apps and their signers come from `PackageManager`
  (`QUERY_ALL_PACKAGES`). Install uses a `PackageInstaller` session (`REQUEST_INSTALL_PACKAGES`) with a mutable
  `PendingIntent`; on `STATUS_PENDING_USER_ACTION` the system confirmation is launched. Sessions left over from a
  previous process are abandoned at start.
  Before a session is created, if the system does not yet let the app install packages, its "Install unknown
  apps" setting is opened and the install waits for the user to come back. Allowed: the install carries on.
  Not allowed: it fails with words that say what to do. (A session committed without the permission is aborted
  by the system as soon as the user leaves for that setting, which reads as "Install was cancelled".)
- **Desktop:** the first device from `adb devices -l` in state `device`. `adb` is looked up in `$ANDROID_HOME`,
  `$ANDROID_SDK_ROOT`, the default SDK location for the OS, then `PATH`. SDK level and ABIs come from `getprop`,
  installed apps from `pm list packages --show-versioncode`, install is `adb install -r`, with
  `--bypass-low-target-sdk-block` on Android 14 and later.
  States: adb not found, no device, unauthorised, ready.
- Every `adb` call carries `-s <serial>` of the chosen device. Before an install the serial is probed again and
  must still be in state `device`.
- Arguments are passed to `adb` as separate process arguments, never through a shell string.
- `adb` queries time out after 15 seconds and an install after 5 minutes; a timed-out process is killed.

## 6. Install flow

`Idle → Downloading(progress) → Verifying → Installing → AwaitingConfirmation (Android) → Done | Failed(reason)`

- One install at a time per package; a second tap while one is running does nothing.
- A failure names the cause in words a person can act on ("Checksum did not match, the download was discarded",
  "Install was cancelled", "INSTALL_FAILED_UPDATE_INCOMPATIBLE: …"). A failed or cancelled install returns the
  package to a state where it can be tried again.
- After `Done` the device's installed list is refreshed.

## 7. Sync

- On launch: load the cached catalogue, then sync if there is none or the last successful check is over 12 hours old.
- Manual refresh is always available.
- The UI shows the app count and when the catalogue was last checked, and shows sync progress and errors without
  blocking browsing of the cached catalogue.

## 8. Acceptance tests (owned by this spec, written before the code)

Naming: `test_{unit}_{condition}_{expectedResult}`. Fixtures in `shared/src/desktopTest/resources/fixtures/`:
the real `entry.jar` and a five-package slice of the real index. Test classes: `EntryVerifierTest`,
`SyncRulesTest`, `IndexReaderTest`, `CatalogRulesTest`, `RepoNamesTest`, `ApkInspectorTest`, `AdbOutputTest`.
`ApkInspectorTest` uses a real 9 kB APK from the repository.

Not covered by automated tests, verified by hand on a device: the `PackageInstaller` flow, `PackageManager`
visibility, and a live end-to-end sync.

Verified by hand on 2026-10-06, Android 16 (API 36) emulator, arm64: first sync (`JarFile` accepts `entry.jar`
on Android), browse, search, Installed and Updates shelves, install and update through `PackageInstaller`
including the unknown-apps detour (allowed and declined) and a cancelled confirmation, and from desktop an
install and an update through `adb`. Not yet tried: a physical phone, and Android older than 16.

## 9. Critic dispositions (2026-09-30)

| # | Finding | Disposition |
|---|---|---|
| 1 | Entry tests pass with a broken chain | Fixed: added forged-manifest and signature-file tamper tests. A jar re-signed with another key exercises the same check as a tampered signature file, so no separate test. |
| 2 | Signature is SHA256withRSA, `JarFile` works | Fixed: hand-rolled PKCS#7 dropped, §1 corrected, `JarFile` used. Confirmed on Android 16 (emulator, 2026-10-06). |
| 3 | DER walker underspecified | Moot after 2. |
| 4 | Signer data dropped | Fixed for Android (model, rules, tests). Desktop cannot read the installed signer; it reports adb's failure text. Deferred. |
| 5 | adb calls without a serial | Fixed: `-s` everywhere, re-probe before install, test on the command builder. |
| 6 | Rooted names escape the cache dir and the base URL | Fixed: §2.4, §2.5, `RepoNamesTest`. |
| 7 | PackageInstaller flow incomplete | Fixed: `AwaitingConfirmation`, cancel handling, permissions, stale sessions. |
| 8 | `targetSdk` dropped | Fixed: `targetSdk`, `maxSdk`, `minTargetSdk`, adb bypass flag. |
| 9 | ABI test passes without a filter; no ABI preference | Fixed: tests use a device whose ABI is not the highest code; preference rule added. |
| 10 | No timeouts, stale APKs | Timeouts fixed. APKs are deleted after every attempt. Cancel button deferred. |
| 11 | "Every byte" claim false | Fixed by narrowing the claim in §2; icon hash check deferred. Icon locale pick fixed. |
| 12 | `maxAge`, size caps | Size caps fixed. Staleness warning from `maxAge` deferred. |
