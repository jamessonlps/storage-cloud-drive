# Cloud Drive S3

<p align="center">
  <strong>Personal cloud storage Android app powered by Amazon S3</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-26%2B-green?logo=android" alt="Min SDK 26" />
  <img src="https://img.shields.io/badge/Kotlin-1.9-purple?logo=kotlin" alt="Kotlin 1.9" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue?logo=jetpackcompose" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/AWS%20SDK-Kotlin%201.0-orange?logo=amazonaws" alt="AWS SDK" />
  <img src="https://img.shields.io/badge/ExoPlayer-Media3%201.2-red" alt="ExoPlayer" />
  <img src="https://img.shields.io/badge/Encryption-AES--256--GCM-darkgreen" alt="AES-256-GCM" />
</p>

---

## About

**Cloud Drive S3** turns an Amazon S3 bucket into your personal cloud drive. The app lets you upload photos, videos, audio and any file from your phone to S3, browse stored files, preview them directly on the device, and download them back — all with optional end-to-end encryption and a modern Material Design 3 interface.

---

## Features

### File Management

| Feature | Description |
|---|---|
| **File upload** | Send any file type to your S3 bucket |
| **Multi-file upload** | Select multiple files at once to upload |
| **Share Intent upload** | Share files directly from other apps into Cloud Drive S3 |
| **File download** | Save files from S3 to `Downloads/CloudDriveS3/` |
| **Create folders** | Organize your files by creating folders inside the bucket |
| **Delete files** | Remove files from the bucket with a confirmation dialog |
| **Folder navigation** | Browse the full folder hierarchy inside your bucket |

### File Preview

| Feature | Description |
|---|---|
| **Image preview** | View images with zoom and pan (pinch-to-zoom, double-tap) |
| **Image gallery** | Swipe horizontally between images in the same folder |
| **Video preview** | Play videos with native controls (ExoPlayer / Media3) |
| **Audio preview** | Play audio with a custom UI (play/pause, seek bar, duration, ±10s skip) |
| **PDF preview** | View PDFs with page-by-page navigation (native PdfRenderer) |
| **File type icons** | Distinct icons for images, videos, audio, documents, and other types |
| **Thumbnails** | Image thumbnails loaded directly from S3 (via Coil) |
| **Grid / List mode** | Toggle between grid and list view |

### Gallery Sync

| Feature | Description |
|---|---|
| **Automatic backup** | Sync photos and videos from the device gallery to S3 automatically |
| **Folder selection** | Choose which gallery folders to sync via a BottomSheet picker |
| **Wi-Fi only mode** | Restrict sync to Wi-Fi connections (default: enabled) |
| **Periodic sync** | Runs every 6 hours via WorkManager with battery-aware constraints |
| **Real-time detection** | ContentObserver detects new photos and triggers sync within 30 seconds |
| **Sync status** | Track total, synced, pending, and failed files with a progress card |
| **One-way backup** | Deleting from the device does not delete from S3 |
| **Per-profile config** | Each AWS profile has independent sync settings and bucket |
| **Custom S3 prefix** | Configurable prefix for the S3 key structure (default: `gallery-sync/`) |

### Transfers

| Feature | Description |
|---|---|
| **Transfer queue** | Manage uploads and downloads in a queue with individual status |
| **Real-time progress** | Track the progress of each transfer as a percentage |
| **Parallel transfers** | Up to 3 simultaneous transfers (configurable semaphore) |
| **Automatic retry** | Exponential backoff retry on failure |
| **Multipart upload** | Large files are split into parts for improved reliability |
| **Background transfers** | Uploads and downloads continue even when the app is in the background (Foreground Service) |
| **Progress notifications** | Monitor transfer status from the system notification bar |

### Security

| Feature | Description |
|---|---|
| **AES-256-GCM encryption** | Files are encrypted before upload and decrypted after download |
| **Per-profile key** | Each AWS profile has its own encryption key |
| **Biometric authentication** | Protect app access with fingerprint or Face ID |
| **Secure storage** | Credentials stored with EncryptedSharedPreferences |
| **HTTPS only** | All communication is encrypted in transit |
| **Multiple profiles** | Manage different AWS configurations with quick switching |

### UI

| Feature | Description |
|---|---|
| **Light / Dark / System theme** | Switch themes manually or follow the system setting |
| **Dynamic Colors** | Color palette automatically adapts to the wallpaper (Android 12+) |
| **Transfer badge** | Active transfer count badge on the bottom navigation bar |
| **Material Design 3** | Modern interface with Material 3 components and gestures |

---

## Architecture

The project follows a layered architecture with clear separation of concerns:

```
com.clouddrive/
|
|-- CloudDriveApp.kt              # Application class
|-- MainActivity.kt               # Entry point, Compose host, Share Intent handler
|
|-- crypto/                        # Security layer
|   |-- EncryptionManager.kt      # AES-256-GCM: key generation, encrypt, decrypt
|
|-- s3/                            # Data layer (AWS S3)
|   |-- S3Config.kt               # Config data class: accessKeyId, secretKey, region, bucket
|   |-- S3ClientProvider.kt       # Cached S3Client singleton
|   |-- S3Repository.kt           # CRUD: list, upload, download, delete, headObject
|   |-- SettingsManager.kt        # DataStore: credentials, profiles, encryption settings
|
|-- transfer/                      # Transfer queue layer
|   |-- TransferItem.kt           # Transfer item model (state, progress, type, source)
|   |-- TransferManager.kt        # Singleton: queue, semaphore, retry, encryption
|   |-- RetryPolicy.kt            # Exponential backoff with jitter
|
|-- sync/                          # Gallery sync layer
|   |-- GalleryScanner.kt         # MediaStore queries (images + videos), folder listing
|   |-- GallerySyncWorker.kt      # CoroutineWorker: scan, diff, feed TransferManager
|   |-- GallerySyncScheduler.kt   # WorkManager: periodic (6h) and immediate scheduling
|   |-- GalleryContentObserver.kt # ContentObserver: real-time new photo detection (30s debounce)
|   |-- db/
|       |-- SyncDatabase.kt       # Room database (synced_files table)
|       |-- SyncedFileEntity.kt   # Entity: mediaStoreId, folder, file, status, s3Key
|       |-- SyncedFileDao.kt      # Queries: getPending, getCompleted, getSyncStats (Flow)
|
|-- service/                       # Service layer (Background)
|   |-- TransferService.kt        # Foreground Service for notifications and background work
|
|-- ui/                            # Presentation layer (Jetpack Compose)
    |-- FileListScreen.kt         # Main screen: file listing, navigation, actions
    |-- SettingsScreen.kt         # AWS config, profiles, biometrics, encryption
    |-- GallerySyncScreen.kt      # Gallery sync config, folder picker (BottomSheet), status
    |-- TransferQueueScreen.kt    # Transfer queue with progress indicators
    |-- ImagePreviewDialog.kt     # Single image preview with zoom/pan
    |-- ImageGalleryDialog.kt     # Gallery: swipe between images in the folder
    |-- VideoPreviewDialog.kt     # Video preview with ExoPlayer
    |-- AudioPreviewDialog.kt     # Audio preview with custom UI
    |-- PdfPreviewDialog.kt       # PDF preview with PdfRenderer
    |-- ZoomableImage.kt          # Reusable zoom/pan composable
    |-- S3ImageFetcher.kt         # Coil fetcher for images directly from S3
    |-- theme/
        |-- Theme.kt              # Material 3 + Dynamic Colors
```

### Data Flow

```
[User action]
      |
      v
[FileListScreen] -----> [TransferManager] -----> [EncryptionManager]
      |                        |                        |
      |                        | (encrypted data)       |
      |                        v                        |
      |                 [S3Repository] <---------------'
      |                        |
      |                  [S3ClientProvider]
      |                        |
      |                   [Amazon S3]
      |
      | (preview)
      v
[ImageGalleryDialog / VideoPreviewDialog / AudioPreviewDialog / PdfPreviewDialog]
```

### Encryption Flow

```
Upload:
  File (bytes)
    -> EncryptionManager.encrypt()
    -> [IV (12 bytes) | Ciphertext | GCM Tag (128 bits)]
    -> S3Repository.upload() with metadata { "encrypted": "true" }

Download:
  S3Repository.headObject() -> checks "encrypted" metadata
    -> EncryptionManager.decrypt([IV | Ciphertext])
    -> original bytes
```

---

## Prerequisites

- **JDK 17**
- **Android SDK** (via Android Studio or command-line tools)
- **AWS account** with an S3 bucket created
- **IAM credentials** (Access Key ID + Secret Access Key) with permissions on the bucket

### Required AWS IAM Policy

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:ListBucket",
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:HeadObject"
      ],
      "Resource": [
        "arn:aws:s3:::YOUR-BUCKET-NAME",
        "arn:aws:s3:::YOUR-BUCKET-NAME/*"
      ]
    }
  ]
}
```

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/jamessonlps/storage-cloud-drive.git
cd storage-cloud-drive
```

### 2. Set up the Android SDK

#### Option A: Via terminal (without Android Studio)

```bash
# macOS with Homebrew
brew install --cask android-commandlinetools

# Accept licenses
yes | sdkmanager --sdk_root="/opt/homebrew/share/android-commandlinetools" --licenses

# Install required components
sdkmanager --sdk_root="/opt/homebrew/share/android-commandlinetools" \
  "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

Create `local.properties` at the project root:

```properties
sdk.dir=/opt/homebrew/share/android-commandlinetools
```

#### Option B: Via Android Studio

Open the project and wait for Gradle sync to complete.

### 3. Build

```bash
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

### 4. Install on a device

#### Via ADB (without Android Studio)

1. Enable **USB Debugging** in Settings > Developer Options
2. Connect via USB in "File Transfer" mode
3. Accept the debugging authorization prompt on the device

```bash
export PATH="/opt/homebrew/share/android-commandlinetools/platform-tools:$PATH"
adb devices                                                      # verify device is detected
adb install app/build/outputs/apk/debug/app-debug.apk           # install
adb install -r app/build/outputs/apk/debug/app-debug.apk        # reinstall
```

#### Via Gradle

```bash
./gradlew installDebug   # builds and installs automatically
```

### 5. Configure S3 in the app

On first launch, fill in your credentials in the Settings screen:

| Field | Description | Example |
|---|---|---|
| **Access Key ID** | IAM access key | `AKIAIOSFODNN7EXAMPLE` |
| **Secret Access Key** | IAM secret key | `wJalrXUtnFEMI/K7MDENG/...` |
| **Region** | AWS region of the bucket | `us-east-1` |
| **Bucket Name** | S3 bucket name | `my-personal-drive` |

---

## Usage

### Navigation

- **Main screen**: lists files and folders in the bucket
- **Enter folder**: tap a folder
- **Go back**: tap `..` or the Back button
- **Refresh**: refresh icon in the top bar
- **Toggle grid/list**: icon in the top bar

### File Operations

| Action | How to |
|---|---|
| Upload 1 file | `+` button > select file |
| Upload multiple files | `+` button > select multiple files |
| Upload via share | Share from another app > select Cloud Drive S3 |
| Download | Download icon on the file |
| Delete | Trash icon > confirm |
| Create folder | Folder icon in the top bar |
| Preview | Tap the file (images, videos, audio, PDFs) |

### Image Gallery

- Tap any image to open the preview
- Swipe horizontally to navigate between images in the folder
- Pinch-to-zoom or double-tap to zoom in
- When zoomed in, drag to pan; when at 1x, swipe to go to the next image

### Video Preview

- Plays directly in the app with native ExoPlayer controls
- Supports all formats handled by the device codec (MP4, MKV, WebM, etc.)

### Audio Preview

- Custom UI with: play/pause, seek bar, duration display, ±10s skip
- Color-coded icon by format (MP3, AAC, OGG, FLAC, etc.)

### PDF Preview

- Rendered via Android's native `PdfRenderer`
- Swipe horizontally to navigate between pages
- "Page X of Y" indicator at the top

### Transfer Queue

- Access via the icon in the bottom navigation bar (shows a badge with the active count)
- Track individual progress for each transfer
- Failed transfers are retried automatically with exponential backoff

### Encryption

1. Go to **Settings > Security**
2. Enable **"AES-256 Encryption"**
3. A unique key is generated for your profile and stored securely
4. From that point on, all uploads are encrypted automatically
5. Downloads of encrypted files are decrypted automatically

> Files uploaded without encryption remain readable normally. Encryption is fully backwards-compatible.

---

## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| **Kotlin** | 1.9.22 | Main language |
| **Jetpack Compose** | BOM 2023.10.01 | Declarative UI framework |
| **Material 3** | Compose M3 | Design system and components |
| **AWS SDK for Kotlin** | 1.0.30 | Amazon S3 communication |
| **Media3 / ExoPlayer** | 1.2.1 | Video and audio playback |
| **DataStore Preferences** | 1.0.0 | Settings persistence |
| **Security Crypto** | 1.1.0-alpha06 | EncryptedSharedPreferences for credentials |
| **Biometric** | 1.1.0 | Biometric authentication |
| **Coil** | 2.5.0 | Image and thumbnail loading |
| **Kotlin Coroutines** | 1.7.3 | Async operations and concurrency |
| **WorkManager** | 2.9.0 | Periodic and immediate gallery sync scheduling |
| **Room** | 2.6.1 | Local database for sync tracking (synced files) |
| **Navigation Compose** | 2.7.6 | Screen navigation |
| **javax.crypto** | Android built-in | AES-256-GCM encryption |
| **PdfRenderer** | Android built-in | PDF rendering |
| **Android Gradle Plugin** | 8.2.2 | Build system |
| **Gradle** | 8.5 | Dependency management |

---

## Security

### Encryption at rest (AES-256-GCM)

- Algorithm: AES-256-GCM (authenticated — provides both confidentiality and integrity)
- 12-byte IV generated randomly per file
- Ciphertext layout: `[IV (12 bytes)][Ciphertext + GCM Tag (128 bits)]`
- 256-bit key generated with `KeyGenerator` and stored in `EncryptedSharedPreferences`
- S3 object receives metadata `x-amz-meta-encrypted: true` for automatic detection on download

### Credentials

- Stored in `EncryptedSharedPreferences` (backed by Android Keystore)
- Never written in plaintext to disk or logs
- Secret Access Key field has a visibility toggle

### Communication

- `usesCleartextTraffic=false` in the Manifest — HTTPS only
- AWS SDK uses TLS 1.2+ by default

> **Production note**: consider **AWS Cognito** or **STS (Security Token Service)** instead of long-lived static IAM keys.

---

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (configure signing in app/build.gradle.kts first)
./gradlew assembleRelease

# Build + install on connected device
./gradlew installDebug

# Full build with checks
./gradlew build

# Lint
./gradlew lint

# Skip lint during development
./gradlew assembleDebug -x lint

# Clean build artifacts
./gradlew clean
```

---

## Build Configuration

| Parameter | Value |
|---|---|
| **Compile SDK** | 34 (Android 14) |
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 34 |
| **Java Target** | JDK 17 |
| **Kotlin Compiler Extension** | 1.5.8 |
| **Gradle** | 8.5 |
| **Android Gradle Plugin** | 8.2.2 |

---

## Roadmap

### High priority

- [ ] **Share files from S3** — generate a pre-signed URL with configurable expiration
- [ ] **File search** — filter by name in the current folder or recursively
- [ ] **Rename files and folders** — copy + delete operation on S3
- [ ] **Move files** — select a destination folder or drag to another folder

### Medium priority

- [ ] **Multi-select** — select multiple files for batch download or deletion
- [ ] **Sort and filter** — sort by name, size, date; filter by type
- [ ] **Favorites** — bookmark files/folders for quick access
- [ ] **Transfer history** — persistent log of past uploads and downloads

### Technical improvements

- [ ] **Unit tests** — coverage for `S3Repository`, `TransferManager`, `EncryptionManager`
- [ ] **Instrumented tests** — UI flows with Compose Testing
- [ ] **ProGuard for release** — enable minification and optimization
- [ ] **Signed release build** — configure keystore and signing config
- [ ] **AWS Cognito** — replace static IAM keys with temporary tokens

### New features

- [ ] **Upload widget** — send files directly from the Android home screen
- [x] **Photo auto-backup** — automatically sync the gallery with S3 (WorkManager + Room + ContentObserver)
- [ ] **Offline mode** — local cache of recently viewed files
- [ ] **Compress before upload** — reduce image and video size before sending
- [ ] **Folder sync** — keep a local folder mirrored in S3

---

## License

This project is distributed under the MIT License. See the `LICENSE` file for details.
