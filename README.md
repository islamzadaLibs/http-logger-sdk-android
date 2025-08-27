# HTTP Logger SDK for Android 🚀

A lightweight and modern **HTTP Logger SDK** for Android that helps you track and debug your **HTTP requests & responses** in real-time.  
It can also store logs in **Firestore** (with your own Firebase project configuration), and comes with a companion **HTTP Logger Dashboard App** on the Play Store.

---

## Features ✨

- 📡 Log **requests & responses** (URL, headers, body, status, duration).  
- 🔍 Multiple logging levels: `NONE`, `BASIC`, `HEADERS`, `FULL`.  
- ☁️ Optional **Firestore integration** – store your logs securely.  
- 📊 Dedicated **Dashboard App** to explore your logs in real-time.  
- ⚡ Plug & Play – works seamlessly with **OkHttp** or any HTTP client.  

---

## Installation ⚙️

### Step 1. Add JitPack repository
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Step 2. Add the dependency
```kotlin
dependencies {
    implementation("com.github.islamzadaLibs:http-logger-sdk-android:Tag")
}
```
> Replace **Tag** with the latest release version. 👉 Check the latest version on **JitPack**

---

## Usage 🚀

### 1. Initialize the Logger
```kotlin
val logger = HttpLogger.Builder()
    .setLogLevel(LogLevel.FULL) // NONE, BASIC, HEADERS, FULL
    .build()
```

### 2. Attach to OkHttp (Example)
```kotlin
val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(logger)
    .build()
```
✅ Now all your HTTP requests & responses will be logged automatically.

---

## Firestore Integration ☁️

You can store logs in your own Firebase Firestore project for later inspection.

**Setup**  
- Add your Firebase project `google-services.json` to your app.

**Initialize the Firestore logger:**
```kotlin
val firestoreLogger = FirestoreLogger(
    projectId = "your-project-id",
    collectionPath = "http_logs"
)

val logger = HttpLogger.Builder()
    .setLogLevel(LogLevel.FULL)
    .enableFirestore(firestoreLogger)
    .build()
```
✅ Now every request/response will also be saved in Firestore.

---

## Dashboard App 📊

We provide a dedicated Android Dashboard App (available on Play Store) to visualize your logs:

- 🔎 Search & filter requests.  
- 📑 Inspect request/response headers & bodies.  
- 📊 Monitor error rates and response times.  

👉 Download the Dashboard App from the Play Store.

---

## Log Levels 🔎

The logger supports multiple levels of detail:
```kotlin
enum class LogLevel {
    NONE,    // No logs
    BASIC,   // Request & Response lines
    HEADERS, // Includes headers
    FULL     // Headers + Body
}
```

---

## Example Log Output 📝
```
--> POST https://api.example.com/v1/login
Headers: Content-Type: application/json
Body: { "username": "test", "password": "***" }

<-- 200 OK (350ms)
Headers: Content-Type: application/json
Body: { "token": "abc123" }
```

---

## License 📄

This project is licensed under the **MIT License** – free to use in commercial and personal projects.

---

## Author ✍️

Developed by **Vusal Islamzada**  
For issues & feature requests, please open an issue.
