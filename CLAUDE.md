# RupeeLog - Claude Code Context

## Project Overview
RupeeLog is a privacy-first personal finance Android app that automatically captures UPI payment notifications and categorizes expenses using AI.

## Tech Stack
- **Android**: Kotlin, Jetpack Compose, Material 3
- **Database**: Room with KSP
- **DI**: Hilt
- **AI**: Rule-based + Gemini API + Gemma 3n (local LLM via MediaPipe)
- **Server**: Embedded Ktor (Netty) for REST API
- **Dashboard**: React + Vite + TypeScript + Tailwind CSS + Recharts

## Project Structure
```
RupeeLog/
├── app/src/main/kotlin/com/rupeelog/
│   ├── MainActivity.kt              # Main entry point with navigation
│   ├── RupeeLogApp.kt              # Hilt Application class
│   ├── agents/                       # AI agents
│   │   ├── core/                     # GeminiClient, LocalLLMClient
│   │   ├── categorization/           # Transaction categorization
│   │   └── reporting/                # Report generation
│   ├── data/
│   │   ├── importer/                 # Statement parsers (CSV, PDF)
│   │   └── local/                    # Room database, preferences
│   ├── di/                           # Hilt modules
│   ├── domain/model/                 # Domain models
│   ├── notification/                 # NotificationListenerService
│   ├── server/                       # Ktor embedded server
│   └── ui/                           # Compose UI screens
└── dashboard/                        # React web dashboard
```

## Key Features
1. **UPI Notification Capture**: Uses NotificationListenerService to capture payment notifications
2. **AI Categorization**: 3-tier system (Rule-based → Local LLM → Cloud API)
3. **Privacy First**: Local-first with optional cloud features
4. **REST API**: Embedded Ktor server on port 8765
5. **Web Dashboard**: React dashboard for desktop viewing
6. **PDF Statement Import**: Password-protected bank statement import (SBI, HDFC, ICICI, Axis, PhonePe)

## Build Commands
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run tests
./gradlew test

# Dashboard (in dashboard/ folder)
npm install && npm run dev
```

## Version History
- **v1.0.0**: Initial release with core features
- **v1.1.0**: PDF bank statement import with password support
  - Supports: SBI (YONO, Email, Net Banking), HDFC, ICICI, Axis, PhonePe
  - Auto-detects bank from filename and shows password format hints
  - Uses PdfBox-Android for PDF parsing

## Important Notes
- Requires Java 17 for building
- APK output: `app/build/outputs/apk/debug/app-debug.apk`
- Local LLM requires Gemma 3n model downloaded to device
