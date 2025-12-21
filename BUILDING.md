# Building and Development

## Prerequisites

- **Java Development Kit (JDK)**: JDK 17 or higher
- **Android SDK**: Android SDK with API level 34 (Android 14)
- **Android Studio**: Latest stable version (recommended for development)
- **Git**: For version control

## Setup Instructions

### 1. Clone the Repository

```bash
git clone https://github.com/machika/Memoiz.git
cd Memoiz
```

### 2. Open in Android Studio

1. Open Android Studio
2. Select "Open an Existing Project"
3. Navigate to the cloned `Memoiz` directory
4. Click "OK"

Android Studio will automatically:
- Download required Gradle dependencies
- Sync the project
- Index the codebase

### 3. Build the Project

#### Using Android Studio:
1. Wait for Gradle sync to complete
2. Click "Build" → "Make Project" (or press `Ctrl+F9` / `Cmd+F9`)
3. Verify no build errors

#### Using Command Line:

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Clean build
./gradlew clean assembleDebug
```

### 4. Run on Device/Emulator

#### Using Android Studio:
1. Connect an Android device (API 29+) or start an emulator
2. Click "Run" → "Run 'app'" (or press `Shift+F10` / `Ctrl+R`)
3. Select target device

#### Using Command Line:

```bash
# Install on connected device
./gradlew installDebug

# Run tests
./gradlew test
```

## Project Structure

```
Memoiz/
├── app/                          # Main application module
│   ├── src/
│   │   └── main/
│   │       ├── java/com/machika/memoiz/
│   │       │   ├── data/         # Database, DAOs, repositories
│   │       │   ├── domain/       # Domain models
│   │       │   ├── service/      # Services (AI, clipboard)
│   │       │   ├── worker/       # WorkManager workers
│   │       │   ├── ui/           # Compose UI
│   │       │   └── MainActivity.kt
│   │       ├── res/              # Resources (layouts, drawables, etc.)
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts          # App module build config
├── build.gradle.kts              # Project build config
├── settings.gradle.kts           # Gradle settings
├── gradle.properties             # Gradle properties
├── README.md                     # Project overview
├── ARCHITECTURE.md               # Architecture documentation
└── BUILDING.md                   # This file
```

## Development Workflow

### Code Style

The project follows Kotlin coding conventions:
- Use 4 spaces for indentation
- Maximum line length: 120 characters
- Follow official [Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html)

### Adding Features

1. Create a feature branch:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. Make changes following the architecture:
   - Database changes → Update entities and DAOs in `data/`
   - Business logic → Add/modify in `domain/` and `service/`
   - UI changes → Update Compose screens in `ui/screens/`

3. Test your changes:
   ```bash
   ./gradlew test
   ./gradlew connectedAndroidTest  # Requires device/emulator
   ```

4. Commit and push:
   ```bash
   git add .
   git commit -m "feat: description of your feature"
   git push origin feature/your-feature-name
   ```

5. Create a Pull Request on GitHub

### Testing

#### Unit Tests
Located in `app/src/test/`:
```bash
./gradlew test
```

#### Instrumentation Tests
Located in `app/src/androidTest/`:
```bash
./gradlew connectedAndroidTest
```

### Debugging

#### Enable Debug Logging
In `app/build.gradle.kts`, debug variant automatically enables logging.

#### Database Inspection
Use Android Studio's App Inspection tool to view Room database contents:
1. Run app on device/emulator
2. View → Tool Windows → App Inspection
3. Select "Database Inspector"

#### WorkManager Debugging
Check WorkManager execution:
```bash
adb shell dumpsys jobscheduler | grep com.machika.memoiz
```

## Common Issues

### Build Fails with "SDK not found"

**Solution**: Set `ANDROID_HOME` environment variable:
```bash
export ANDROID_HOME=/path/to/Android/sdk
```

### Gradle Sync Failed

**Solution**: 
1. File → Invalidate Caches → Invalidate and Restart
2. Or delete `.gradle` directory and re-sync

### Room Database Errors

**Solution**: Clean and rebuild:
```bash
./gradlew clean build
```

### Compose Preview Not Showing

**Solution**: 
1. Build → Make Project
2. Invalidate caches if necessary
3. Ensure you're using Android Studio with Compose support

## Dependency Updates

### Check for Updates
```bash
./gradlew dependencyUpdates
```

### Update Dependencies
Edit `app/build.gradle.kts` and update version numbers.

### Important Dependencies:
- **Room**: Database
- **WorkManager**: Background tasks
- **Compose**: UI framework
- **Navigation**: Screen navigation
- **Coroutines**: Async operations

## Performance Profiling

### CPU Profiler
1. Run app in debug mode
2. View → Tool Windows → Profiler
3. Select CPU profiler

### Memory Profiler
1. Run app in debug mode
2. View → Tool Windows → Profiler
3. Select Memory profiler

### Database Queries
Enable query logging in debug builds by adding to `MemoizDatabase`:
```kotlin
.setQueryCallback({ sqlQuery, bindArgs ->
    Log.d("RoomQuery", "Query: $sqlQuery, Args: $bindArgs")
}, Executors.newSingleThreadExecutor())
```

## Release Build

### Generate Signed APK

1. Build → Generate Signed Bundle / APK
2. Select APK
3. Create or select keystore
4. Choose release build variant
5. Click Finish

### Via Command Line:

```bash
# Create keystore (first time only)
keytool -genkey -v -keystore release.keystore -alias memoiz \
  -keyalg RSA -keysize 2048 -validity 10000

# Build signed release
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=release.keystore \
  -Pandroid.injected.signing.store.password=your_password \
  -Pandroid.injected.signing.key.alias=memoiz \
  -Pandroid.injected.signing.key.password=your_password
```

## CI/CD

The project is ready for CI/CD integration. Example GitHub Actions workflow:

```yaml
name: Android CI

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build with Gradle
        run: ./gradlew assembleDebug
      - name: Run tests
        run: ./gradlew test
```

## Resources

- [Android Developers Documentation](https://developer.android.com/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)

## Getting Help

- **Issues**: Report bugs or request features on GitHub Issues
- **Discussions**: Ask questions in GitHub Discussions
- **Documentation**: See ARCHITECTURE.md for detailed design info

## License

See LICENSE file for details.
