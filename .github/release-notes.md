Desktop builds of Depot for macOS, Windows and Linux. This is an early preview.

**What is and is not verified**

- The desktop app syncs, verifies and browses the live F-Droid repository.
- Installing to a phone through `adb` and the Android app have not yet been run on a device.

**These installers are not code-signed**

- macOS: after copying Depot to Applications, run `xattr -dr com.apple.quarantine /Applications/Depot.app`, or the system refuses to open it.
- Windows: SmartScreen shows a warning; choose "More info", then "Run anyway".

**Check your download**

- `SHA256SUMS` lists the hash of every file: `shasum -a 256 -c SHA256SUMS --ignore-missing`
- Every file has a build attestation proving it was built by this repository's workflow: `gh attestation verify <file> --repo AndroidPoet/Depot`

Installing apps needs `adb` (Android platform-tools) on the computer and USB debugging enabled on the phone.
