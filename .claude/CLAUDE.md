# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Cloud Drive S3** is an Android app that transforms an Amazon S3 bucket into a personal cloud drive. The app enables file uploads, downloads, deletions, folder navigation, and background transfers with a Material Design 3 interface.

**Tech Stack**: Android 26+, Kotlin 1.9, Jetpack Compose, AWS SDK for Kotlin, Room, DataStore, Coroutines

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
   - `BucketEntry.kt` - Data model for bucket listing entries

2. **Transfer Layer** (`transfer/`)
   - `TransferManager.kt` - Singleton orchestrating all transfer operations (upload, download, delete) with coroutine-based concurrency (semaphore limit of 3), batch operations, pause/resume/cancel/retry support
   - `TransferItem.kt` - Data class representing a transfer item, plus enums: `TransferType` (UPLOAD, DOWNLOAD, DELETE), `TransferState` (QUEUED, UPLOADING, DOWNLOADING, DELETING, COMPLETED, FAILED, CANCELLED, RETRYING, PAUSED), `TransferSource` (MANUAL, GALLERY_SYNC)
   - `RetryPolicy.kt` - Exponential backoff retry logic (max 5 retries, 1s→16s delay) for transient network errors

3. **Service Layer** (`service/`)
   - `TransferService.kt` - Foreground Service for background transfers with progress notifications. Observes `TransferManager.transfers` flow and groups notifications by batch. Handles completion notifications per batch.
   - Uses `ACTION_TRANSFER_COMPLETE` broadcast for UI refresh

4. **Crypto Layer** (`crypto/`)
   - `EncryptionManager.kt` - Optional client-side encryption for uploads/downloads. Keys stored per profile via SettingsManager.

5. **Sync Layer** (`sync/`)
   - `GalleryScanner.kt` - Reads MediaStore (Images + Videos), returns gallery folders and files
   - `GallerySyncWorker.kt` - CoroutineWorker: scans gallery, inserts pending files in Room, feeds TransferManager
   - `GallerySyncScheduler.kt` - WorkManager scheduling: periodic (6h) and immediate sync jobs
   - `GalleryContentObserver.kt` - ContentObserver for real-time new photo detection (30s debounce)
   - `db/SyncDatabase.kt` - Room database with `synced_files` table
   - `db/SyncedFileEntity.kt` - Entity tracking each synced file (mediaStoreId, status, s3Key, etc.)
   - `db/SyncedFileDao.kt` - DAO with queries for pending, completed, stats (Flow)

6. **UI Layer** (`ui/`)
   - `FileListScreen.kt` - Main screen: file listing, folder navigation, selection mode, upload/download/delete actions
   - `SettingsScreen.kt` - Configuration screen for AWS credentials and profiles
   - `GallerySyncScreen.kt` - Gallery sync config with ModalBottomSheet folder picker and status card
   - `TransferQueueScreen.kt` - Transfer queue with batch progress, pause/resume/cancel/retry controls, grouped by state (active, completed, failed)
   - `BucketListScreen.kt` - Multi-bucket listing and selection screen
   - `ImagePreviewDialog.kt` / `ImageGalleryDialog.kt` - Image preview with swipe gallery and zoom (`ZoomableImage.kt`)
   - `VideoPreviewDialog.kt` - Video file preview
   - `AudioPreviewDialog.kt` - Audio file preview
   - `PdfPreviewDialog.kt` - PDF file preview
   - `S3ImageFetcher.kt` - Coil integration for loading S3 images as thumbnails
   - `theme/Theme.kt` - Material 3 theming with Dynamic Colors support (Android 12+)

### Key Data Flow

1. **Initialization**: `CloudDriveApp` → `MainActivity` → `FileListScreen` or `SettingsScreen`
2. **Configuration**: User fills `SettingsScreen` → credentials saved in `SettingsManager` → `S3ClientProvider` initializes
3. **File Operations**: `FileListScreen` enqueues items in `TransferManager` → `TransferService` runs as foreground service → Progress via Notifications + `BroadcastReceiver`
4. **Delete Operations**: Both single-file and batch deletes run through `TransferManager` (same queue as uploads/downloads), providing progress tracking, notifications, and retry support
5. **Credentials**: Stored securely in DataStore (Android private app storage), never hardcoded

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

### Background Transfers (TransferManager + TransferService)

- **TransferManager** (singleton): Manages transfer queue via `StateFlow<List<TransferItem>>`, supports UPLOAD, DOWNLOAD, and DELETE types
- **Batch operations**: `enqueueBatch()` groups items by `batchId` for unified progress/notifications
- **Concurrency**: Semaphore limits to 3 simultaneous transfers
- **Pause/Resume/Cancel/Retry**: Full lifecycle control per batch via `pauseBatch()`, `resumeBatch()`, `cancelBatch()`, `retryFailedInBatch()`
- **TransferService**: Foreground Service (required by Android 12+) with persistent notification, observes `TransferManager.transfers` flow
- **Notifications**: Per-batch progress notifications (grouped when multiple batches), completion notifications with type-aware icons and labels
- **UI refresh**: `ACTION_TRANSFER_COMPLETE` broadcast fires per item completion; `FileListScreen` listens via `BroadcastReceiver` to auto-refresh
- Must have `POST_NOTIFICATIONS` permission (Android 13+)

### Material 3 / Dynamic Colors

- Automatic theme switching (light/dark) based on system settings
- Dynamic Colors support on Android 12+ (automatically adapts to system palette)
- Base theme defined in `app/src/main/res/values/themes.xml`

## Common Patterns in This Codebase

1. **Singleton for AWS Client**: `S3ClientProvider.getInstance()` ensures single S3Client instance
2. **Transfer Queue**: All file operations (upload, download, delete) go through `TransferManager.enqueue()` / `enqueueBatch()` — never call `S3Repository` directly from UI for long-running operations
3. **DataStore Access**: `SettingsManager` wraps DataStore operations (getConfig, saveConfig)
4. **Error Handling**: Try-catch blocks around S3 calls, errors shown via Snackbar or Toast. `RetryPolicy` handles transient network errors with exponential backoff.
5. **Folder Navigation**: Uses S3 prefix pattern (e.g., `folder1/subfolder/`) — "/" is treated as folder separator, ".." means parent
6. **File Type Icons**: Icons determined by file extension (image, video, audio, document)
7. **Portuguese UI**: All user-facing strings are in Brazilian Portuguese. Pay attention to gender agreement (e.g., "Exclusão concluída" not "concluído")

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
3. Long-running operations (uploads/downloads/deletes) must use `TransferManager`, not main thread or direct `S3Repository` calls
4. Add new feature branches from the default branch: `claude/android-s3-cloud-storage-dBVZG`
5. Follow Kotlin naming conventions and official code style (`kotlin.code.style=official` in gradle.properties)
