# Quick Setup Checklist for Firebase App Distribution Workflow

Use this checklist to quickly set up the GitHub Actions workflow for automated APK builds and Firebase App Distribution.

## Prerequisites
- [ ] You have a Firebase project
- [ ] Your Android app is registered in Firebase
- [ ] You have owner/admin access to both GitHub repository and Firebase project

## Step 1: Firebase Console Setup (5 minutes)

### Download google-services.json
- [ ] Go to [Firebase Console](https://console.firebase.google.com/)
- [ ] Select your project
- [ ] Click gear icon → Project Settings
- [ ] Scroll to "Your apps" section
- [ ] Click "Download google-services.json" button
- [ ] Keep this file open for the next step

### Get Firebase App ID
- [ ] In the same Project Settings page
- [ ] Under "Your apps", find your Android app
- [ ] Copy the "App ID" (format: `1:123456789:android:abc123def456`)
- [ ] Save this ID in a notepad

### Enable Firebase App Distribution
- [ ] In Firebase Console left sidebar
- [ ] Click "Release & Monitor" → "App Distribution"
- [ ] Click "Get Started" if not already enabled

### Create Tester Group
- [ ] In App Distribution, go to "Testers & Groups" tab
- [ ] Click "Add Group"
- [ ] Name: `testers` (or any name you prefer)
- [ ] Add tester email addresses (can be your own email for testing)
- [ ] Click "Save"

### Create Service Account
- [ ] In Firebase Console, click gear icon → Project Settings
- [ ] Go to "Service accounts" tab
- [ ] Click "Manage service account permissions" (opens Google Cloud Console)
- [ ] Click "Create Service Account"
- [ ] Name: `github-actions-firebase` (or any name)
- [ ] Click "Create and Continue"
- [ ] Select role: "Firebase App Distribution Admin"
- [ ] Click "Continue" → "Done"
- [ ] Click on the newly created service account
- [ ] Go to "Keys" tab
- [ ] Click "Add Key" → "Create new key"
- [ ] Choose JSON format
- [ ] Click "Create" (downloads a JSON file)
- [ ] Keep this JSON file open for the next step

## Step 2: GitHub Secrets Setup (3 minutes)

- [ ] Go to your GitHub repository
- [ ] Click "Settings" → "Secrets and variables" → "Actions"

### Add GOOGLE_SERVICES_JSON Secret
- [ ] Click "New repository secret"
- [ ] Name: `GOOGLE_SERVICES_JSON`
- [ ] Value: Copy the entire content of `google-services.json` file
- [ ] Click "Add secret"

### Add FIREBASE_APP_ID Secret
- [ ] Click "New repository secret"
- [ ] Name: `FIREBASE_APP_ID`
- [ ] Value: Paste the App ID you copied earlier (e.g., `1:123456789:android:abc123def456`)
- [ ] Click "Add secret"

### Add FIREBASE_SERVICE_CREDENTIALS Secret
- [ ] Click "New repository secret"
- [ ] Name: `FIREBASE_SERVICE_CREDENTIALS`
- [ ] Value: Copy the entire content of the service account JSON file you downloaded
- [ ] Click "Add secret"

### Verify Secrets
- [ ] You should now have exactly 3 secrets:
  - `GOOGLE_SERVICES_JSON`
  - `FIREBASE_APP_ID`
  - `FIREBASE_SERVICE_CREDENTIALS`

## Step 3: Test the Workflow (5 minutes)

### Manual Test
- [ ] Go to "Actions" tab in your GitHub repository
- [ ] Click on "Build APK and Deploy to Firebase App Distribution" workflow
- [ ] Click "Run workflow" button
- [ ] Select branch: `main` or your current branch
- [ ] Add release notes: `Test build` (optional)
- [ ] Click "Run workflow"

### Monitor Execution
- [ ] Wait for workflow to start (may take a few seconds)
- [ ] Click on the workflow run to see details
- [ ] Watch each step execute (should take 5-10 minutes)
- [ ] Verify all steps complete successfully (green checkmarks)

### Verify Build Artifacts
- [ ] Scroll down on the workflow run page
- [ ] Under "Artifacts" section, verify you see:
  - `debug-apk`
  - `release-apk`
- [ ] (Optional) Download and verify the APK files

### Verify Firebase Distribution
- [ ] Go back to Firebase Console → App Distribution
- [ ] Click on "Releases" tab
- [ ] Verify you see the new build
- [ ] Check the version number and release notes

### Check Tester Notification
- [ ] Check the email inbox of the tester(s) you added
- [ ] You should receive an email from Firebase App Distribution
- [ ] Email contains a download link for the APK
- [ ] (Optional) Click the link and verify you can download the APK

## Step 4: Configure for Production (Optional)

### Customize Tester Group Name
If you used a different name than "testers":
- [ ] Edit `.github/workflows/firebase-app-distribution.yml`
- [ ] Find line with `groups: testers`
- [ ] Change to your group name
- [ ] Commit and push the change

### Set Up Multiple Environments
For staging/beta/production:
- [ ] Create additional tester groups in Firebase (e.g., `beta`, `production`)
- [ ] Create separate workflow files or add conditions to deploy to different groups
- [ ] See `.github/workflows/README.md` for advanced configuration

### Add Build Signing for Release
For signed release builds:
- [ ] Create a keystore file
- [ ] Encode it in base64: `base64 -i your_keystore.jks`
- [ ] Add as GitHub Secret: `KEYSTORE_BASE64`
- [ ] Add additional secrets: `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
- [ ] Update workflow to use signing (see `.github/workflows/README.md`)

## Troubleshooting

### Workflow fails at "Create google-services.json"
- [ ] Verify `GOOGLE_SERVICES_JSON` secret is set
- [ ] Check the JSON is valid (no extra spaces, complete content)
- [ ] Re-download from Firebase and update the secret

### Workflow fails at "Build Debug APK"
- [ ] Check build logs for specific error
- [ ] Verify `google-services.json` is created correctly
- [ ] Try building locally: `./gradlew assembleDebug`

### Workflow fails at "Deploy to Firebase App Distribution"
- [ ] Verify `FIREBASE_APP_ID` matches your app in Firebase Console
- [ ] Check `FIREBASE_SERVICE_CREDENTIALS` is valid JSON
- [ ] Verify service account has "Firebase App Distribution Admin" role
- [ ] Check Firebase App Distribution is enabled in Firebase Console

### Build succeeds but testers don't receive notification
- [ ] Verify tester emails are correct in Firebase Console
- [ ] Check if group name in workflow matches Firebase group name
- [ ] Look in spam/junk folders
- [ ] Try manually inviting testers from Firebase Console

### "Permission denied" errors
- [ ] Verify all GitHub Secrets are set correctly
- [ ] Check service account permissions in Google Cloud Console
- [ ] Ensure service account has not been deleted or disabled

## Success Indicators

You've successfully set up the workflow when:
- [x] Workflow runs without errors
- [x] APK artifacts are uploaded to GitHub
- [x] Build appears in Firebase App Distribution
- [x] Testers receive email notification
- [x] Testers can download and install the APK

## Next Steps

After successful setup:
- [ ] Add more testers to the group
- [ ] Set up automatic triggers (push to main/develop)
- [ ] Configure branch protection rules
- [ ] Set up code review requirements
- [ ] Consider setting up automated testing before deployment

## Need Help?

Refer to detailed documentation:
- `.github/workflows/README.md` - Complete setup guide
- `CI_CD_IMPLEMENTATION.md` - Implementation details
- [Firebase App Distribution Docs](https://firebase.google.com/docs/app-distribution)
- [GitHub Actions Docs](https://docs.github.com/en/actions)

## Notes
- Keep your service account JSON secure - never commit it to the repository
- Regularly rotate service account keys for security
- Monitor Firebase quota and usage in Firebase Console
- GitHub Actions has usage limits for free tier - monitor your usage
