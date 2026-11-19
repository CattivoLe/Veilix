# Contributing

Thanks for helping. A few things keep the project easy to review:

- Run `./gradlew testDebugUnitTest` before you open a pull request.
- Keep `core/` free of Android UI code. It's the part other implementations
  port.
- **Format changes need three things together:** an update to
  [docs/FORMAT.md](docs/FORMAT.md), new files in [test-vectors](test-vectors)
  with a row in the table at the end of FORMAT.md, and a version bump that
  keeps old images readable. Images people already made must keep opening.
- No network access, analytics or crash reporting. The app has no `INTERNET`
  permission, and that won't change.
- New UI strings go into `values/strings.xml`, and into `values-ru` and
  `values-sr` if you can. Otherwise say so in the pull request.

Wanted: LZFSE and LZMA decompression on Android, so every iOS image opens, and
ports of the format to other platforms or to a command-line tool.
