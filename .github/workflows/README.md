# Firebase App Distribution GitHub Actions Setup

This document explains how to configure the GitHub Actions workflow for building APKs and deploying them to Firebase App Distribution.

## Workflow Overview

The workflow (`firebase-app-distribution.yml`) automatically:
1. Builds both debug and release APKs
2. Uploads APKs as GitHub artifacts
3. Deploys the debug APK to Firebase App Distribution for testing

## Triggers

The workflow runs on:
- **Push to main or develop branches**: Automatic deployment
- **Push tags matching `v*`**: Version releases (e.g., v1.0.0)
- **Manual dispatch**: Run manually from GitHub Actions UI with custom release notes

## Required GitHub Secrets

You need to configure the following secrets in your GitHub repository settings:

### 1. GOOGLE_SERVICES_JSON

The `google-services.json` file from Firebase Console.

**How to get it:**
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Go to Project Settings (gear icon)
4. Under "Your apps", find your Android app
5. Download `google-services.json`

**How to add to GitHub:**
1. Open the downloaded file and copy its entire content
2. Go to your GitHub repository
3. Settings → Secrets and variables → Actions
4. Click "New repository secret"
5. Name: `GOOGLE_SERVICES_JSON`
6. Value: Paste the entire JSON content
7. Click "Add secret"

### 2. FIREBASE_APP_ID

Your Firebase Android App ID (format: `1:123456789:android:abc123def456`).

**How to get it:**
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Go to Project Settings
4. Under "Your apps", find your Android app
5. Copy the "App ID" (starts with `1:`)

**How to add to GitHub:**
1. Go to your GitHub repository
2. Settings → Secrets and variables → Actions
3. Click "New repository secret"
4. Name: `FIREBASE_APP_ID`
5. Value: Paste the App ID
6. Click "Add secret"

### 3. FIREBASE_SERVICE_CREDENTIALS

Service account JSON for Firebase App Distribution API access.

**How to get it:**
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Go to Project Settings (gear icon)
4. Click on "Service accounts" tab
5. Click "Generate new private key"
6. Download the JSON file
7. Open the file and copy its entire content

**Alternative method using Google Cloud Console:**
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your project
3. Navigate to "IAM & Admin" → "Service Accounts"
4. Click "Create Service Account"
5. Name it (e.g., "github-actions-firebase-distribution")
6. Grant it "Firebase App Distribution Admin" role
7. Click "Done"
8. Click on the created service account
9. Go to "Keys" tab
10. Click "Add Key" → "Create new key"
11. Choose "JSON" format
12. Download and copy the JSON content

**How to add to GitHub:**
1. Go to your GitHub repository
2. Settings → Secrets and variables → Actions
3. Click "New repository secret"
4. Name: `FIREBASE_SERVICE_CREDENTIALS`
5. Value: Paste the entire JSON content
6. Click "Add secret"

## Firebase App Distribution Setup

### Enable Firebase App Distribution

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. In the left sidebar, click "Release & Monitor" → "App Distribution"
4. If not enabled, click "Get Started"

### Create Tester Groups

The workflow deploys to a group called "testers". You need to create this group:

1. In Firebase App Distribution, go to "Testers & Groups" tab
2. Click "Add Group"
3. Name: `testers`
4. Add tester email addresses
5. Save

You can customize the group name by editing the workflow file:
```yaml
groups: testers  # Change this to your group name
```

## Manual Workflow Dispatch

To manually trigger a build:

1. Go to your GitHub repository
2. Click on "Actions" tab
3. Select "Build APK and Deploy to Firebase App Distribution" workflow
4. Click "Run workflow" button
5. Select the branch
6. Optionally add release notes
7. Click "Run workflow"

## Workflow Outputs

### GitHub Artifacts

The workflow uploads two artifacts that can be downloaded from the Actions run page:
- **debug-apk**: Debug APK (retained for 30 days)
- **release-apk**: Release APK (retained for 90 days)

### Firebase App Distribution

Testers in the "testers" group will:
1. Receive an email notification about the new build
2. Be able to download and install the APK from Firebase App Distribution
3. See the release notes included in the deployment

## Version Information

The app uses Git commit count for versioning:
- **versionCode**: Number of commits in the repository
- **versionName**: Format `0.0.<commit_count>`

This is calculated automatically by the `getGitCommitCount()` function in `app/build.gradle.kts`.

## Troubleshooting

### Build fails with "google-services.json not found"
- Make sure the `GOOGLE_SERVICES_JSON` secret is set correctly
- Verify the JSON content is valid (no extra spaces or newlines)

### Firebase deployment fails with "Invalid credentials"
- Verify the `FIREBASE_SERVICE_CREDENTIALS` secret contains valid JSON
- Check that the service account has "Firebase App Distribution Admin" role
- Make sure the service account is for the correct Firebase project

### APK not showing in Firebase App Distribution
- Verify the `FIREBASE_APP_ID` matches your Android app in Firebase Console
- Check that App Distribution is enabled in Firebase Console
- Verify the "testers" group exists and has members

### Workflow runs but testers don't receive notification
- Check that testers are added to the "testers" group in Firebase Console
- Verify tester email addresses are correct
- Check spam/junk folders

## Customization

### Change distribution group
Edit the workflow file and change the `groups` parameter:
```yaml
groups: your-group-name
```

### Deploy release APK instead of debug
Edit the workflow file and change the `file` parameter:
```yaml
file: app/build/outputs/apk/release/app-release.apk
```

### Add code signing for release builds
You'll need to:
1. Create a keystore file
2. Add keystore as GitHub secret (base64 encoded)
3. Add keystore password, alias, and key password as secrets
4. Update the workflow to decode and use the keystore

Example additional steps:
```yaml
- name: Decode Keystore
  env:
    KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
  run: |
    echo "$KEYSTORE_BASE64" | base64 -d > app/release.keystore

- name: Build Signed Release APK
  env:
    KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
  run: |
    ./gradlew assembleRelease \
      -Pandroid.injected.signing.store.file=release.keystore \
      -Pandroid.injected.signing.store.password=$KEYSTORE_PASSWORD \
      -Pandroid.injected.signing.key.alias=$KEY_ALIAS \
      -Pandroid.injected.signing.key.password=$KEY_PASSWORD
```

## Security Best Practices

1. **Never commit secrets**: Don't commit `google-services.json`, keystores, or credentials to the repository
2. **Use GitHub Secrets**: Always store sensitive data in GitHub Secrets
3. **Limit access**: Only grant necessary permissions to service accounts
4. **Rotate credentials**: Periodically rotate service account keys
5. **Monitor usage**: Check Firebase App Distribution usage in Firebase Console

## Additional Resources

- [Firebase App Distribution Documentation](https://firebase.google.com/docs/app-distribution)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Firebase Distribution GitHub Action](https://github.com/wzieba/Firebase-Distribution-Github-Action)
- [Android Build Documentation](https://developer.android.com/studio/build)
