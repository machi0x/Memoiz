# Memoiz

An intelligent Android app that automatically categorizes text, links, and images using on-device AI and saves them as organized memos.

## Features

### Core Functionality
- **Multi-Modal AI Processing**: Uses a sophisticated pipeline of on-device ML Kit GenAI APIs to understand and categorize different types of content.
- **Intelligent Text Analysis**:
  - **URLs**: Fetches the content of web pages to generate a relevant sub-category and a concise summary.
  - **Long Articles**: Automatically creates a one-sentence summary for long blocks of text.
  - **Short Text**: Identifies meaningful short text and assigns a relevant category.
- **Image Recognition**: Analyzes images to generate a descriptive sub-category, making them searchable and organized.
- **Content-Specific Categorization**: Assigns special categories for "Web site", "Image", and "Uncategorizable" content, providing a clear and organized structure.
- **Privacy-First**: All AI processing happens on-device, ensuring your data never leaves your device.
- **Seamless Integration**: Captures content via the system's "Process Text" action, the Android share sheet (`ACTION_SEND`), and an in-app paste button.

### User Features
- **Grouped Memos**: Memos are automatically grouped by their AI-generated category.
- **Detailed Memo Cards**: Each memo displays its content, sub-category, summary (if available), source app, and timestamp.
- **Category and Memo Management**: Full CRUD operations for both memos and entire category groups.
- **Modern UI**: A clean and intuitive interface built with Jetpack Compose and Material 3.

## Architecture

### Technology Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Database**: Room for persistent storage
- **Asynchronous Operations**: Kotlin Coroutines for background tasks.
- **AI**: A multi-API pipeline using ML Kit's on-device GenAI capabilities:
    - `genai-prompt`: For open-ended categorization and sub-category generation.
    - `genai-summarization`: For creating concise summaries of long text.
    - `genai-image-description`: For generating descriptive captions for images.
- **Networking**: OkHttp and Jsoup for fetching and parsing web content from URLs.
- **Navigation**: Compose Navigation
- **Minimum SDK**: 29 (Android 10)

### Data Processing Flow
1.  **Input**: The app receives data (text, URL, or image) from the user.
2.  **Triage**: It identifies the content type.
3.  **Processing**:
    - **URL**: Fetches web content, then passes it to the Prompt and Summarization APIs.
    - **Image**: Uses the Image Description API to generate a caption.
    - **Text**: Passes the text to the Prompt and (if necessary) Summarization APIs.
4.  **Storage**: The structured result (category, sub-category, summary, etc.) is saved to the Room database.
5.  **Display**: The main screen displays the memos, grouped by category, in a `LazyColumn`.

## How to Use

1. **Install the app** on an Android device (API 29+).
2. **Grant permissions** as requested.
3. **Share content** to be categorized:
   - Select text → tap "Categorize with Memoiz" in the context menu.
   - Share any text, URL, or image → choose Memoiz from the share sheet.
   - Open Memoiz → tap the "Paste from clipboard" button.
4. **View your organized memos**, grouped by category, in the main screen.

## Privacy

- **On-Device Processing**: All AI analysis happens on your device. Your content is never sent to the cloud.
- **Explicit Action Required**: The app only processes content when you explicitly share or paste it.
- **Compliant**: Fully respects Android 10+ clipboard and data access restrictions.

## Future Enhancements

- [ ] Implement `ACTION_SEND_MULTIPLE` to handle sharing multiple items at once.
- [ ] Allow user to create and manage their own custom categories.
- [ ] Allow user to edit memos and their categories.
- [ ] Full search functionality across all memos.
- [ ] Export and backup functionality.

## Requirements

- Android 10 (API 29) or higher
- Kotlin 1.9+
- Gradle 8.2+

## CI/CD

This project includes a GitHub Actions workflow for automated builds and Firebase App Distribution. For more details, see [.github/workflows/README.md](.github/workflows/README.md)

## License

See the LICENSE file for details.
