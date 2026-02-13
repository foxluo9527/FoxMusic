# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FoxMusic is an Android music streaming application built with Jetpack Compose and modern Android architecture patterns. The app features online music playback, playlists, social features, and a unique desktop lyrics overlay system.

## Essential Build Commands

### Building the App
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug build on connected device
./gradlew installDebug

# Clean build
./gradlew clean
```

### Running Tests
```bash
# Run all unit tests
./gradlew test

# Run unit tests for specific module
./gradlew :core:player:test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run tests with coverage
./gradlew testDebugUnitTest
```

### Code Quality
```bash
# Lint checks
./gradlew lint

# Lint for specific module
./gradlew :app:lint
```

## Architecture Overview

### Multi-Module Structure

The project follows clean architecture with strict module separation:

- **app/** - Application module, navigation setup, main activity
- **core/** - Shared infrastructure modules (no inter-dependencies between core modules)
  - **common** - Utilities, extensions, MVI base classes
  - **model** - Domain models (shared data classes)
  - **domain** - Business logic interfaces, repository contracts, use cases
  - **data** - Repository implementations, mappers (DTO → Domain)
  - **network** - Retrofit services, interceptors, auth handling
  - **database** - Room database, DAOs, entities
  - **datastore** - Preferences storage
  - **ui** - Shared Compose components
  - **player** - Media3/ExoPlayer integration, music playback service
- **feature/** - Independent feature modules (can only depend on core modules)
  - **home**, **auth**, **player**, **playlist**, **search**, **discover**, **social**, **chat**, **profile**

**Rule**: Feature modules cannot depend on other feature modules. All shared code belongs in core modules.

### Music Player Architecture

The music player uses Media3/ExoPlayer with a service-based architecture:

1. **MusicPlaybackService** (`core/player/src/main/java/com/fox/music/core/player/service/MusicPlaybackService.kt`)
   - Extends `MediaSessionService` for background playback
   - Manages ExoPlayer instance with 2GB LRU cache for audio files
   - Provides MediaSession for system media controls

2. **MusicController** (`core/player/src/main/java/com/fox/music/core/player/controller/MusicController.kt`)
   - Singleton interface exposing player controls (play, pause, next, previous, seekTo, etc.)
   - Exposes `playerState: StateFlow<PlayerState>` for reactive UI updates
   - Two playlist update modes:
     - `updatePlaylist()` - Seamless transition, keeps current song playing
     - `setPlaylist()` - Full replacement, starts from beginning

3. **Position Updates** - Player position emitted every 500ms via coroutine for smooth progress bars

4. **Metadata Handling** - Converts `Music` domain models to Media3 `MediaItem` with embedded lyrics, artwork, artist info

### Desktop Lyrics Feature

A unique floating lyrics overlay that displays synchronized lyrics while the app is in the background:

1. **LyricSyncManager** (`feature/player/src/main/java/com/fox/music/feature/player/lyric/manager/LyricSyncManager.kt`)
   - Singleton managing overlay window lifecycle
   - Observes `MusicController.playerState` for real-time updates
   - Uses `WindowManager.addView()` with `TYPE_APPLICATION_OVERLAY`
   - Requires `SYSTEM_ALERT_WINDOW` permission (handled via XXPermissions)
   - Auto-hides after 10 seconds of inactivity
   - Initialized in `FoxMusicApplication.onCreate()`

2. **LyricStyleManager** (`feature/player/src/main/java/com/fox/music/feature/player/lyric/manager/LyricStyleManager.kt`)
   - Singleton managing lyric styling (fonts, colors, spacing)
   - Stores preferences in SharedPreferences
   - Provides preset styles: CLASSIC, NEON, WARM, COOL, DARK
   - Exposes `styleFlow: StateFlow<LyricStyle>` for reactive updates

3. **DesktopLyricContainer** (`feature/player/src/main/java/com/fox/music/feature/player/lyric/ui/DesktopLyricContainer.kt`)
   - Custom view with draggable positioning
   - Uses LyricViewX library for synchronized lyric rendering
   - Control buttons: prev, play/pause, next, favorite, settings, exit

### MVI Pattern

All ViewModels extend `MviViewModel<S, I, E>` from `core/common/mvi/MviViewModel.kt`:

- **UiState** - Immutable state representation
- **UiIntent** - User/system actions sent via `sendIntent()`
- **UiEffect** - One-time events sent via `sendEffect()`
- **updateState()** - Type-safe state reducer

Example flow:
```kotlin
User clicks button → sendIntent(HomeIntent.LoadMusic)
  → handleIntent() processes it
  → updateState { copy(isLoading = true) }
  → repository call
  → updateState { copy(musicList = result, isLoading = false) }
```

### Repository Pattern

All data access goes through repositories:

1. **Interface** in `core/domain/repository/` (e.g., `MusicRepository`)
2. **Implementation** in `core/data/repository/` (e.g., `MusicRepositoryImpl`)
3. **Binding** via Hilt in `core/data/di/DataModule.kt`

All repository methods return `Result<T>` sealed class:
- `Result.Success(data)` - Successful response
- `Result.Error(exception, message)` - Error case
- `Result.Loading` - Loading state

### Dependency Injection (Hilt)

- All modules use `@Module @InstallIn(SingletonComponent::class)`
- Key DI modules:
  - `core/network/di/NetworkModule.kt` - Retrofit, OkHttp, API services
  - `core/data/di/DataModule.kt` - Repository bindings
  - `core/database/di/DatabaseModule.kt` - Room database
  - `core/player/di/PlayerModule.kt` - MusicController binding

### Network Layer

- **Retrofit + OkHttp3** with Kotlinx Serialization (not Gson)
- **Interceptors** in `core/network/di/NetworkModule.kt`:
  - `AuthInterceptor` - Adds auth tokens to requests
  - `TokenAuthenticator` - Handles token refresh on 401 responses
  - `HttpLoggingInterceptor` - Debug logging (BODY level in debug builds)
- **API Services** are modular: `MusicApiService`, `PlaylistApiService`, `AuthApiService`, etc.
- Base URL from `BuildConfig.BASE_URL`

### Database Layer

- **Room Database** (`core/database/FoxMusicDatabase.kt`)
- Single database file: `fox_music.db`
- Tables: Music, Playlist, User, Message, SearchHistory
- Version 1 with `fallbackToDestructiveMigration()` for development

### Navigation

- Jetpack Navigation Compose with route constants (e.g., `HOME_ROUTE`, `PLAYER_ROUTE`)
- Centralized in `app/src/main/java/com/fox/music/ui/MainScreen.kt`
- Bottom navigation with 5 destinations: Home, Discover, Search, Playlist, Profile
- Auth-gated: `MainActivityViewModel` redirects to login if not authenticated

## Key Conventions

### Module Dependencies
- Feature modules can only depend on core modules
- Core modules should not depend on each other (exception: common can be used by all)
- App module depends on all features and core modules

### State Management
- Use `StateFlow` for hot observables (player state, auth state)
- Use `Flow` with Paging3 for paginated data
- All state updates must be immutable (use `copy()`)

### Coroutines
- All async operations use `suspend` functions
- Use `viewModelScope` in ViewModels for automatic cancellation
- No callbacks or RxJava

### Error Handling
- Wrap all repository calls with `Result` type
- Use `suspendRunCatching { }` in repository implementations
- Show user-friendly error messages via `UiEffect`

### URL Handling in Player
- MusicController prepends base URL if relative paths provided
- Absolute URLs are used as-is
- Handles both streaming URLs and cached local files

## File Location Quick Reference

### Entry Points
- Application initialization: `app/src/main/java/com/fox/music/FoxMusicApplication.kt`
- Main activity: `app/src/main/java/com/fox/music/MainActivity.kt`
- Navigation setup: `app/src/main/java/com/fox/music/ui/MainScreen.kt`

### Core Infrastructure
- MVI base class: `core/common/src/main/java/com/fox/music/core/common/mvi/MviViewModel.kt`
- Result type: `core/common/src/main/java/com/fox/music/core/common/result/Result.kt`
- Music controller: `core/player/src/main/java/com/fox/music/core/player/controller/MusicControllerImpl.kt`
- Playback service: `core/player/src/main/java/com/fox/music/core/player/service/MusicPlaybackService.kt`

### Desktop Lyrics
- Sync manager: `feature/player/src/main/java/com/fox/music/feature/player/lyric/manager/LyricSyncManager.kt`
- Style manager: `feature/player/src/main/java/com/fox/music/feature/player/lyric/manager/LyricStyleManager.kt`
- Container view: `feature/player/src/main/java/com/fox/music/feature/player/lyric/ui/DesktopLyricContainer.kt`

### Configuration
- Dependencies: `gradle/libs.versions.toml`
- Modules: `settings.gradle.kts`
- App config: `app/build.gradle.kts`

## Tech Stack

- **Language**: Kotlin 2.3.0
- **UI**: Jetpack Compose (BOM 2026.01.01)
- **DI**: Hilt 2.59
- **Player**: Media3/ExoPlayer 1.9.1
- **Network**: Retrofit 3.0.0 + OkHttp 5.3.2
- **Serialization**: Kotlinx Serialization 1.10.0
- **Database**: Room 2.8.4
- **Async**: Coroutines 1.10.2
- **Paging**: Paging3 3.4.0
- **Image Loading**: Coil 2.7.0
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 36

## Recent Features

- Desktop lyrics overlay with draggable controls (commit 3c9223b)
- Player UI optimizations (commit 988a2cc)
- Media URL processing for API integration (commit fa89435)
