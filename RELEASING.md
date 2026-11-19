# Releasing

Releases are built by [`.github/workflows/release.yml`](.github/workflows/release.yml)
when a `v*` tag is pushed.

## One-time setup

1. Create the signing key. Keep it, and a backup, **outside the repository**.
   If you lose it, existing users can't update to builds signed with a new key.

   ```bash
   keytool -genkeypair -v -keystore veilix-release.jks -alias veilix \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Add these repository secrets (Settings → Secrets and variables → Actions):

   | Secret | Value |
   |---|---|
   | `VEILIX_KEYSTORE_BASE64` | output of `base64 -i veilix-release.jks` |
   | `VEILIX_KEYSTORE_PASSWORD` | keystore password |
   | `VEILIX_KEY_ALIAS` | `veilix` |
   | `VEILIX_KEY_PASSWORD` | key password |

3. Put the certificate fingerprint in the README so people can check the APK:

   ```bash
   keytool -list -v -keystore veilix-release.jks -alias veilix | grep SHA256
   ```

## Each release

1. Raise `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add a `## <versionName>` section to `CHANGELOG.md`. It becomes the release
   notes.
3. Commit, then tag and push:

   ```bash
   git tag v1.2
   git push origin main v1.2
   ```

The workflow refuses a tag that doesn't match `versionName`, runs the tests,
checks the signature and publishes the APK with its SHA-256.

## Local release build

Create an untracked `keystore.properties` in the project root, as shown in the
README, and run `./gradlew assembleRelease`.
