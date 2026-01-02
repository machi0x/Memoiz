# PR Build Release APK Workflow

This workflow automatically builds release APKs for Pull Requests, making it easy to test changes before merging.

## Triggers

The workflow runs in two scenarios:

### 1. Automatic Build on PR Events
The workflow automatically triggers when:
- A new Pull Request is opened
- A Pull Request is synchronized (new commits pushed)
- A Pull Request is reopened

### 2. Manual Build via PR Comment
You can manually trigger a build by commenting on a Pull Request with any of these keywords:
- `/build`
- `build apk`
- `build release`

**Example comments:**
- `/build` - Simple command
- `Can someone build apk for testing?` - Natural language
- `Please build release apk` - Full phrase

The workflow will:
1. React to your comment with 👀 (eyes) to acknowledge it's starting
2. Build the release APK
3. React with 🚀 (rocket) on success or 😕 (confused) on failure
4. Post a comment with build results and download link

## Workflow Steps

1. **Checkout Code**: Fetches the PR code with full git history
2. **Setup JDK 17**: Configures Java environment for building
3. **Create google-services.json**: Generates Firebase configuration from secrets
4. **Decode Keystore**: Prepares release signing key
5. **Build Release APK**: Compiles and signs the release APK
6. **Get Version Info**: Extracts version name and code from the build
7. **Upload Artifact**: Stores the APK for download (30 days retention)
8. **Comment on PR**: Posts build status with download link

## Downloading the APK

After a successful build, you can download the APK in two ways:

### Method 1: From PR Comment
The workflow posts a comment on the PR with a direct link to the workflow run where you can download the APK.

### Method 2: From Actions Tab
1. Go to the **Actions** tab in the repository
2. Find the **PR Build Release APK** workflow run
3. Scroll down to **Artifacts** section
4. Download the artifact named `PR-{number}-release-apk`

## Required GitHub Secrets

This workflow requires the same secrets as the Firebase App Distribution workflow:

- `GOOGLE_SERVICES_JSON`: Firebase configuration file content
- `KEYSTORE_BASE64`: Base64-encoded release keystore
- `KEYSTORE_PASSWORD`: Keystore password
- `KEY_ALIAS`: Key alias name
- `KEY_PASSWORD`: Key password

See the main [Firebase App Distribution README](./README.md) for detailed setup instructions.

## Permissions

The workflow requires the following GitHub token permissions:
- `contents: read` - To checkout code
- `issues: write` - To comment on PRs
- `pull-requests: write` - To access PR information
- `actions: read` - To generate artifact links

These are automatically provided by GitHub Actions default token.

## Example Workflow

1. **Developer creates a PR** → APK is automatically built
2. **Reviewer wants to test** → Comments `/build` on the PR
3. **Workflow runs** → Adds 👀 reaction, builds APK, adds 🚀 reaction
4. **Reviewer downloads APK** → Clicks link in the build comment
5. **Reviewer tests** → Installs and tests the APK on their device

## Differences from Firebase App Distribution Workflow

| Feature | PR Build | Firebase Distribution |
|---------|----------|----------------------|
| **Trigger** | PRs and PR comments | Push to main/develop |
| **Output** | GitHub Artifacts | Firebase + GitHub Artifacts |
| **Retention** | 30 days | 90 days |
| **Distribution** | Manual download | Automatic to testers |
| **Use Case** | Testing before merge | Production releases |

## Troubleshooting

### Build not triggering on comment
- Make sure the comment is on a Pull Request (not a regular issue)
- Check that you used one of the trigger keywords: `/build`, `build apk`, or `build release`
- Verify the workflow file is present in the target branch

### Build fails with signing errors
- Verify all signing secrets are properly configured
- Check that secrets are accessible to PRs from your repository
- For PRs from forks, secrets are not available for security reasons

### Cannot download artifact
- Artifacts are retained for 30 days, check if it's still available
- Make sure you're logged into GitHub
- Try downloading from the Actions tab instead of the PR comment link

## Security Note

For security reasons, workflows triggered by `pull_request` events from forks do not have access to secrets. This means:
- PRs from **your repository branches** will build successfully ✅
- PRs from **forked repositories** will fail if they require secrets ❌

If you need to support builds from forks, you'll need to modify the workflow to use `pull_request_target` event (not recommended without additional security measures).

## Customization

### Change artifact retention
Edit the workflow file and modify:
```yaml
retention-days: 30  # Change to desired number of days (1-90)
```

### Add more trigger keywords
Edit the `check-comment` job and add keywords:
```javascript
const shouldBuild = isPR && (
  comment.includes('/build') || 
  comment.includes('build apk') || 
  comment.includes('build release') ||
  comment.includes('your custom keyword')  // Add here
);
```

### Disable automatic PR builds
If you only want comment-triggered builds, change:
```yaml
on:
  issue_comment:
    types: [created]
```

Remove the `pull_request` trigger section.
