# G.I APK Builder — Golden Edition

Android app starter for importing an Android project ZIP, uploading it to a GitHub repository, dispatching a GitHub Actions workflow, polling the run status, and downloading the APK artifact back to the phone.

## UI
Cyber Gold + Neon Green + Dark, Kotlin + Jetpack Compose.

## Important limitations
- This is a functional starter project, not a published/production-tested app.
- The GitHub repository must contain `.github/workflows/android-build.yml` from this package on its default branch before the app can dispatch builds.
- The app uploads the selected ZIP as `incoming/project.zip` to the configured repository. Each build replaces that file, so do not run concurrent builds in the same repository.
- Imported projects must be valid Gradle Android projects and should produce `app/build/outputs/apk/**`.
- GitHub Actions usage limits apply. Private repository access and Actions permissions must be enabled.
- A Personal Access Token is stored locally using Android EncryptedSharedPreferences. No client-side storage is perfectly safe on a compromised/rooted device. Prefer a narrowly scoped fine-grained token and never share the APK with the token inside it.
- Signing is configured in the workflow using GitHub Secrets. Without secrets, the workflow builds a debug APK and returns it as an artifact.
- For production, consider a backend or GitHub App flow instead of a personal token in the Android client.

## GitHub repository setup
1. Create a private repository, e.g. `GI-APK-Builder-Engine`.
2. Copy `.github/workflows/android-build.yml` from this package into that repository and commit it to the default branch.
3. In Settings → Actions → General, allow workflows and grant the repository the needed default permissions. The workflow declares `contents: read` and `actions: write` only where needed.
4. Create a fine-grained PAT with access to this repository. Minimum permissions commonly needed: Contents (read/write), Actions (read/write), Metadata (read). GitHub may require workflow permission when changing workflow files; this app does not modify workflow files.
5. In the app, enter owner, repository name, and token, then test the connection.
6. Import a ZIP containing a Gradle Android project and tap Build APK.

## Optional release signing
Add these repository Actions secrets:
- `ANDROID_KEYSTORE_BASE64`: base64-encoded JKS/PKCS12 keystore
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

If all four are configured, the workflow signs a release APK using `apksigner`. Otherwise it builds and returns a debug APK. Never commit keystore files or passwords.

## Build the G.I APK Builder app
Open this folder in Android Studio and build the `app` module. Android Studio will generate/use a compatible Gradle wrapper. Minimum SDK 26, target SDK 35.
