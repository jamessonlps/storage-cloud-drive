# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Cloud Drive S3** is an Android app that transforms an Amazon S3 bucket into a personal cloud drive. The app enables file uploads, downloads, folder navigation, and background transfers with a Material Design 3 interface.

**Tech Stack**: Android 26+, Kotlin 1.9, Jetpack Compose, AWS SDK for Kotlin, DataStore, Coroutines

## Common Development Commands

### Build
```bash
./gradlew assembleDebug       # Debug APK to app/build/outputs/apk/debug/
./gradlew assembleRelease     # Release APK (configure signing in app/build.gradle.kts first)
```

### Testing & Verification
```bash
./gradlew build               # Full build with checks
./gradlew lint               # Run linting checks
```

### Development
```bash
./gradlew installDebug        # Build and install to connected device/emulator
./gradlew assembleDebug -x lint  # Skip linting during development
```

### Clean
```bash
./gradlew clean               # Clean all build artifacts
```

## Architecture Overview

The codebase follows a **layered architecture** with clear separation of concerns:

### Layers

1. **Data Layer** (`s3/`)
   - `S3ClientProvider.kt` - Singleton that manages the AWS S3Client (cached)
   - `S3Repository.kt` - CRUD operations: list objects, upload, download, delete
   - `SettingsManager.kt` - Persists AWS credentials via DataStore (secure local storage)
   - `S3Config.kt` - Data class for AWS configuration (accessKeyId, secretAccessKey, region, bucketName)

2. **Service Layer** (`service/`)
   - `TransferService.kt` - Foreground Service for background uploads/downloads with progress notifications
   - Uses notification progress and BroadcastReceiver for UI updates

3. **Sync Layer** (`sync/`)
   - `GalleryScanner.kt` - Reads MediaStore (Images + Videos), returns gallery folders and files
   - `GallerySyncWorker.kt` - CoroutineWorker: scans gallery, inserts pending files in Room, feeds TransferManager
   - `GallerySyncScheduler.kt` - WorkManager scheduling: periodic (6h) and immediate sync jobs
   - `GalleryContentObserver.kt` - ContentObserver for real-time new photo detection (30s debounce)
   - `db/SyncDatabase.kt` - Room database with `synced_files` table
   - `db/SyncedFileEntity.kt` - Entity tracking each synced file (mediaStoreId, status, s3Key, etc.)
   - `db/SyncedFileDao.kt` - DAO with queries for pending, completed, stats (Flow)

4. **UI Layer** (`ui/`)
   - `FileListScreen.kt` - Main screen (~320 lines): file listing, folder navigation, upload/download/delete actions
   - `SettingsScreen.kt` - Configuration screen for AWS credentials
   - `GallerySyncScreen.kt` - Gallery sync config with ModalBottomSheet folder picker and status card
   - `theme/Theme.kt` - Material 3 theming with Dynamic Colors support (Android 12+)

### Key Data Flow

1. **Initialization**: `CloudDriveApp` → `MainActivity` → `FileListScreen` or `SettingsScreen`
2. **Configuration**: User fills `SettingsScreen` → credentials saved in `SettingsManager` → `S3ClientProvider` initializes
3. **File Operations**: `FileListScreen` calls `S3Repository` → `TransferService` for background work → Progress via Notifications
4. **Credentials**: Stored securely in DataStore (Android private app storage), never hardcoded

### State Management

- **UI State**: Compose `mutableStateOf()` for local UI state (current folder path, loading states)
- **Persistent State**: DataStore for AWS credentials
- **Service Communication**: BroadcastReceiver for transfer progress updates from `TransferService`

## Important File Locations

- **Manifest**: `app/src/main/AndroidManifest.xml` - Permissions (INTERNET, READ_EXTERNAL_STORAGE, POST_NOTIFICATIONS), component declarations
- **Build Config**: `app/build.gradle.kts` - Dependencies, SDK versions, ProGuard rules
- **Gradle Wrapper**: `gradle/wrapper/gradle-wrapper.properties` - Gradle 8.5 version
- **Resources**: `app/src/main/res/values/strings.xml` - String resources
- **FileProvider Config**: `app/src/main/res/xml/file_paths.xml` - Paths for downloads access

## Key Technical Details

### Dependencies to Know

- **AWS SDK for Kotlin** (`aws.sdk.kotlin:s3:1.0.30`) - S3 client, handles all AWS communication
- **Jetpack Compose BOM** (`2024.01.00`) - Modern declarative UI framework
- **Material 3** - Design system with dynamic theming
- **DataStore Preferences** (`1.0.0`) - Type-safe persistent storage (replaces SharedPreferences)
- **Coil** (`2.5.0`) - Image loading library for thumbnails/previews
- **Kotlin Coroutines** (`1.7.3`) - Async operations for network calls and file I/O

### Coroutines Usage

All S3 operations are suspend functions in `S3Repository`. UI calls happen via `LaunchedEffect` or button click handlers with `viewModelScope` or `rememberCoroutineScope`.

### Security Notes

- **No Cleartext Traffic**: `usesCleartextTraffic=false` in Manifest ensures HTTPS only
- **Credentials Storage**: DataStore is encrypted by default (backed by EncryptedSharedPreferences on supported devices)
- **Password Toggle**: Secret Access Key uses `PasswordVisualTransformation` UI for visibility control
- **IAM Permissions**: Users need S3 ListBucket, GetObject, PutObject, DeleteObject on their bucket

### Background Transfers (TransferService)

- **Foreground Service** with persistent notification (required by Android 12+)
- Progress updates via Notification and BroadcastReceiver
- Survives app being pushed to background or destroyed
- Must have `POST_NOTIFICATIONS` permission (Android 13+)

### Material 3 / Dynamic Colors

- Automatic theme switching (light/dark) based on system settings
- Dynamic Colors support on Android 12+ (automatically adapts to system palette)
- Base theme defined in `app/src/main/res/values/themes.xml`

## Common Patterns in This Codebase

1. **Singleton for AWS Client**: `S3ClientProvider.getInstance()` ensures single S3Client instance
2. **DataStore Access**: `SettingsManager` wraps DataStore operations (getConfig, saveConfig)
3. **Error Handling**: Try-catch blocks around S3 calls, errors shown via Snackbar or Toast
4. **Folder Navigation**: Uses S3 prefix pattern (e.g., `folder1/subfolder/`) — "/" is treated as folder separator, ".." means parent
5. **File Type Icons**: Icons determined by file extension (image, video, audio, document)

## Testing Considerations

- **Manual Testing**: Connect Android device or emulator (API 26+), configure S3 credentials in app settings
- **Emulator S3 Access**: Use real AWS credentials in settings; emulator can reach AWS with internet connectivity
- **File Permissions**: Manually grant READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE if targeting Android 12 or lower
- **Notification Permissions**: Grant POST_NOTIFICATIONS on Android 13+ devices for progress updates

## Build Configuration

- **Compile SDK**: 34 (Android 14)
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34
- **Java Target**: JDK 17
- **Kotlin Compiler**: 1.5.8
- **Gradle**: 8.5
- **ProGuard**: Enabled for release builds with custom rules for AWS SDK

## Notes for Contributors

1. All S3 operations should use `S3Repository` methods (centralized error handling)
2. UI updates that depend on S3 calls should use Compose states + coroutines
3. Long-running operations (uploads/downloads) must use `TransferService`, not main thread
4. Add new feature branches from the default branch: `claude/android-s3-cloud-storage-dBVZG`
5. Follow Kotlin naming conventions and official code style (`kotlin.code.style=official` in gradle.properties)
