# HTTP Logger SDK & Dashboard 🚀

A complete **HTTP monitoring & debugging solution** for Android, including an **SDK** and a **real-time Dashboard app**.

---

## 📦 Contents

1. **HTTP Logger SDK for Android**  
   - An OkHttp interceptor that logs detailed HTTP request/response information.
   
2. **HTTP Logger Dashboard App**  
   - Visualizes, filters, and analyzes logs stored in Firestore.

---

## ✨ Key Features

### 🔍 Detailed Logging
- Request/Response headers & bodies  
- Performance metrics (duration, size, DNS, SSL times)  
- Network information (type, operator, signal strength)  
- Device & app context  
- Session tracking & error categorization  

### 🎯 Smart Configuration
- 4 log levels: `NONE`, `BASIC`, `HEADERS`, `FULL`  
- Environment-based configuration: development, staging, production  
- Sensitive data filtering  
- Memory management: configurable response size limits  

### 📊 Real-Time Dashboard
- Live log tracking  
- Advanced filtering (method, status code, search)  
- Detailed log inspection  
- Multi-project support  

---

## 🚀 Installation

### 1. Add JitPack Repository
```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Add Dependencies
```kotlin
// build.gradle.kts (app module)
dependencies {
    implementation("com.github.islamzadaLibs:http-logger-sdk-android:1.1.0")
}
```

---

## 📖 Usage

### 1. Basic Setup
```kotlin
// Firebase config
val firebaseConfig = FirebaseConfig.create(
    projectId = "your-project-id",
    applicationId = "your-app-id",
    apiKey = "your-firebase-api-key",
    storageBucket = "your-storage-bucket" // optional
)

// Create logger interceptor
val interceptor = HttpLoggerSDK.createInterceptor(
    context = this,
    firebaseConfig = firebaseConfig,
    apiKey = "unique-api-key", // for grouping logs
    config = LoggerConfig.forDevelopment(),
    environment = "development",
    userId = "user-id" // optional
)

// Add to OkHttp client
val client = OkHttpClient.Builder()
    .addInterceptor(interceptor)
    .build()
```

### 2. Advanced Configuration
```kotlin
val customConfig = LoggerConfig(
    isEnabled = true,
    logLevel = LogLevel.FULL,
    includeHeaders = true,
    includeRequestBody = true,
    includeResponseBody = true,
    maxResponseBodySize = 2 * 1024 * 1024 // 2MB
)

val serviceConfigs = mapOf(
    "authService" to HttpLoggerSDK.ServiceConfig(
        apiKey = "auth_service_key",
        loggerConfig = LoggerConfig.forDevelopment()
    ),
    "apiService" to HttpLoggerSDK.ServiceConfig(
        apiKey = "api_service_key",
        loggerConfig = LoggerConfig.forProduction()
    )
)
```

### 3. Environment-Based Config
```kotlin
val devInterceptor = HttpLoggerSDK.createInterceptor(
    config = LoggerConfig.forDevelopment(),
    environment = "development"
)

val prodInterceptor = HttpLoggerSDK.createInterceptor(
    config = LoggerConfig.forProduction(),
    environment = "production"
)
```

---

## 🔧 Firebase Setup

1. Create Firebase project and add Firestore database.
2. Add Android app and download `google-services.json`.
3. Firestore rules example:
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /http_logs/{apiKey}/logs/{logId} {
      allow read, write: if true; // tighten for production
    }
  }
}
```

---

## 📊 Dashboard App
- Real-time log tracking
- Advanced filtering (method, status code, search)
- Detailed log details
- Performance charts
- Multi-project support
- Dark mode

[Download from Play Store](https://play.google.com/store/apps/details?id=com.islamzada.httploggerdashboard)

---

## 🛠 Developer Tools

### Configuration Validation
```kotlin
val report = HttpLoggerSDK.validateConfigurations(
    firebaseConfig = firebaseConfig,
    loggerConfigs = mapOf("service1" to LoggerConfig.forDevelopment())
)
report.printReport()
```

### Diagnostics
```kotlin
val diagnostics = HttpLoggerSDK.getDetailedDiagnosticInfo(context)
println("SDK Version: ${diagnostics["sdkVersion"]}")
```

---

## ⚙️ Configuration Options
| Parameter | Description | Default |
|-----------|-------------|---------|
| isEnabled | Enable/disable logging | true |
| logLevel | Log detail level | LogLevel.FULL |
| includeHeaders | Log headers | true |
| includeRequestBody | Log request body | true |
| includeResponseBody | Log response body | true |
| maxResponseBodySize | Max response size | 1MB |

Predefined configs:
```kotlin
LoggerConfig.forDevelopment()  // Full logging
LoggerConfig.forTesting()      // Headers only
LoggerConfig.forProduction()   // Minimal logging
LoggerConfig.disabled()        // Disabled
```

---

## 🚨 Security
- Automatic sensitive data filtering: `Authorization`, `Cookie`, `X-API-Key`, `X-Auth-Token`, `Bearer`, `X-Access-Token`
- Production-safe configuration example:
```kotlin
val safeConfig = LoggerConfig(
    logLevel = LogLevel.BASIC,
    includeHeaders = false,
    includeRequestBody = false,
    includeResponseBody = false
)
if (safeConfig.isProductionSafe()) println("Production-safe")
```

---

## 📈 Performance Tips
- Memory usage estimation per log
- Response size limits
- Batch log cleanup

---

## 🔍 Debugging
- Log levels: `NONE`, `BASIC`, `HEADERS`, `FULL`
- Common issues: Firebase connection, permissions, network access

---

## 🤝 Contributing
- Fork the repo
- Create feature branch
- Commit your changes
- Push & open a Pull Request

---

## 📜 License
MIT License. See LICENSE file for details.

---

## 👨‍💻 Author
Vusal Islamzada  
Email: vusalislamzada.dev@gmail.com  
GitHub: https://github.com/islamzadavusal
LinkedIn: www.linkedin.com/in/islamzada

