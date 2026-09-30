# Verifying a Depot download

Every release carries three checksum files and a build attestation for each installer. The release workflow also
downloads its own published files and runs all of the checks below before it finishes.

## 1. Checksum

Download the installer and `SHA256SUMS` into the same folder.

| System | Command |
|---|---|
| macOS | `shasum -a 256 -c SHA256SUMS --ignore-missing` |
| Linux | `sha256sum -c SHA256SUMS --ignore-missing` |
| Windows (PowerShell) | `Get-FileHash .\Depot-<version>-windows-x64.msi -Algorithm SHA256`, then compare with the line in `SHA256SUMS` |

`SHA512SUMS` works the same way with `shasum -a 512` / `sha512sum` / `-Algorithm SHA512`.

`MD5SUMS` is there for tools that only speak MD5 (`md5sum -c MD5SUMS --ignore-missing`, `md5 <file>` on macOS,
`Get-FileHash -Algorithm MD5` on Windows). MD5 only catches a broken download. It does not protect against a file
that someone replaced on purpose, so use SHA-256 for that.

## 2. Build attestation

A checksum proves the file matches what the release page lists. The attestation proves the file was built by this
repository's GitHub Actions workflow from a known commit, which a changed release page could not fake.

```
gh attestation verify Depot-<version>-<platform>.<ext> --repo AndroidPoet/Depot
```

This needs the [GitHub CLI](https://cli.github.com).

## What this does not cover

The installers are not code-signed with an Apple Developer ID or a Windows certificate, so the operating system
will not vouch for them. The two checks above are the way to confirm a download is genuine.
