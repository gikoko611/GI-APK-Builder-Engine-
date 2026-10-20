# G.I APK Builder — Golden Cyber Edition

Fresh Android source project. Includes a cyber-themed Compose UI, GitHub repository/branch/token settings, workflow dispatch, workflow run polling, local build history, selectable console, copy console, project ZIP picker, appearance/build/security preferences, diagnostics, and GitHub Actions APK artifact link.

## Build
Open in Android Studio with JDK 17 and Android SDK 35, or push to GitHub and use `.github/workflows/android-build.yml`.

## Configure
Settings → GitHub Connection:
- Repository: `owner/repository`
- Branch: `main`
- GitHub Personal Access Token with repository Contents and Actions permissions.

## Honest feature notes
The app triggers a workflow in the configured repository and polls run status. Project ZIP selection is included, but automatic uploading of arbitrary ZIP contents to GitHub is not implemented. Artifact access opens the GitHub artifact download URL; GitHub may require sign-in. The UI's auto-download option is currently a preference, not a background download engine. Logo/icon assets are intentionally omitted so you can add your own later.
