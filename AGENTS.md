# AGENTS.md - Guide for Agentic Coding Agents

This document contains essential information for agentic coding agents working on this Android Kotlin project.

## Project Overview
- **Type**: Android mobile application (Kotlin)
- **Architecture**: MVVM with Compose UI
- **Package**: `com.example.msp_app`
- **Min SDK**: 24, **Target SDK**: 35
- **Language**: Kotlin with official code style
- **Build System**: Gradle with Kotlin DSL

## Build Commands

### Gradle Commands
```bash
# Build the app
./gradlew build

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing config)
./gradlew assembleRelease

# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests "com.example.msp_app.ExampleUnitTest"

# Run a specific test method
./gradlew test --tests "com.example.msp_app.ExampleUnitTest.addition_isCorrect"

# Run Android lint analysis
./gradlew lint

# Run lint on a specific variant
./gradlew lintDebug

# Check code (includes lint)
./gradlew check

# Clean build
./gradlew clean

# Install debug APK to connected device
./gradlew installDebug

# Unit tests: app/src/test/ | Instrumented tests: app/src/androidTest/
```

## Code Style Guidelines

### General Style
- Follow Kotlin official code style (`kotlin.code.style=official` in gradle.properties)
- Use 4-space indentation (no tabs)
- Line length: 120 characters max
- No comments unless explicitly required

### Import Organization
```kotlin
// Android imports
import android.app.Application
import android.util.Log

// AndroidX imports
import androidx.compose.foundation.layout.*
import androidx.lifecycle.AndroidViewModel

// Third-party libraries
import com.google.firebase.auth.FirebaseAuth
import retrofit2.Retrofit

// Project imports (grouped by feature/module)
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.api.BaseApi
import com.example.msp_app.features.auth.viewModels.AuthViewModel
```

### Package Structure
```
com.example.msp_app/
├── core/           # Utils, viewmodel base, cache, network, sync
├── data/           # API, local DB, repositories, models
├── features/       # Feature modules (auth, payments, sales...)
└── components/     # Reusable UI components
```

### Naming Conventions
- **Classes**: PascalCase (`AuthViewModel`, `BaseApi`)
- **Functions/Variables**: camelCase (`getUserData`, `userData`)
- **Constants**: SCREAMING_SNAKE_CASE (`Constants.APP_VERSION`)
- **Private properties**: underscore prefix (`_userData`, `_isRefreshing`)
- **Composable functions**: PascalCase (`LoginScreen`, `DrawerContainer`)

### File Naming
- ViewModels: `[Feature]ViewModel.kt`
- Screens: `[Feature]Screen.kt`
- Utilities: `[Purpose]Utils.kt`

## Architecture Patterns

### MVVM with Compose
- ViewModels extend `AndroidViewModel` or `BaseOfflineViewModel<T>`
- State: Use `StateFlow` and `MutableStateFlow`
- UI: Compose functions with `@Composable`
- Dependency Injection: Manual DI through constructors/providers

### Offline-First
- Extend `BaseOfflineViewModel<T>` for offline support
- Use `BaseOfflineCache<T>` for caching
- Implement `SyncableEntity` for syncable data
- Handle network states with `ConnectivityMonitor`

### State Management
```kotlin
private val _state = MutableStateFlow<ResultState<List<T>>>(ResultState.Idle)
val state: StateFlow<ResultState<List<T>>> = _state.asStateFlow()

// In Compose
val state by viewModel.state.collectAsState()
```

## Error Handling

### ResultState<T> Sealed Class
```kotlin
sealed class ResultState<T> {
    object Idle : ResultState<T>()
    object Loading : ResultState<T>()
    data class Success<T>(val data: T) : ResultState<T>()
    data class Error<T>(val message: String, val exception: Throwable? = null) : ResultState<T>()
    data class Offline<T>(val data: T, val isExpired: Boolean = false) : ResultState<T>()
}
```

### Guidelines
- Use `Result<T>` for network operations
- Wrap coroutines in try-catch
- Log errors with `Log.e(TAG, message, exception)`
- Provide user-friendly error messages

## API & UI Components
- API: Use `BaseApi`, `ApiProvider.create()`, 300s timeout, Gson
- Compose: Material 3, extract reusable components, use `remember` for local state
- Theme: Use `ThemeController` for dark/light mode, access colors via `MaterialTheme.colorScheme`

## Key Libraries
- Compose BOM, Navigation Compose, Firebase Auth & Firestore
- Retrofit, Room, Coroutines, WorkManager, Google Maps Compose

## Testing Requirements
- Run unit tests: `./gradlew test`
- Run lint: `./gradlew lint`
- Follow AAA pattern (Arrange, Act, Assert)

## Important Notes
- Never commit API keys or sensitive data
- Always handle network connectivity states
- Follow offline-first design principles
- Implement proper error states in UI
