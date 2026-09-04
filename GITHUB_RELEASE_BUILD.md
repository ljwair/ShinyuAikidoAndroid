# Shinyu Aikido v1.3.12 release build

The final release package is built in two controlled stages:

1. GitHub Actions uses a standard Ubuntu runner and the official Android SDK to build and validate an aligned unsigned release APK.
2. The APK is downloaded to the owner's Android phone and signed locally with the permanent private Shinyu release key.

The private signing key is never uploaded to GitHub.

## GitHub Actions output

Run the workflow named `Build validated unsigned Android release`.
Download the artifact named `Shinyu-Aikido-v1.3.12-unsigned`.
It contains:

- `Shinyu-Aikido-v1.3.12-aligned-unsigned.apk`
- `unsigned-build-verification.txt`

Do not distribute the unsigned APK. Sign it locally with the permanent Shinyu keystore first.
