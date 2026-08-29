# Android improvements branch

This branch adds:
- Retrofit + Moshi networking for the joke API
- Hilt dependency injection
- ViewModel (HiltViewModel) and Compose state handling
- DataStore-based caching of last fetched joke
- Unit tests for the ViewModel using Kotlin coroutines test

How to test locally
1. Checkout the branch scaffold/android-improvements
2. Open `mobile` in Android Studio and let it sync Gradle
3. Run unit tests from the IDE or command line: `./gradlew :app:test`
4. Run the app on an emulator/device and press "جلب نكتة"
