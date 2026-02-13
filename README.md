# RupeeLog

A privacy-first personal finance Android app that automatically tracks your UPI payments and categorizes expenses using AI.

## Features

- **Automatic Transaction Capture**: Captures UPI payment notifications from apps like Google Pay, PhonePe, Paytm, etc.
- **AI-Powered Categorization**: Smart categorization using a 3-tier AI system:
  - Rule-based categorizer for common merchants
  - Local LLM (Gemma 3n) for privacy-focused on-device inference
  - Cloud AI (Gemini) for complex categorization
- **Privacy First**: All data stored locally on device. Cloud AI is optional.
- **Budget Tracking**: Set monthly budgets and track spending
- **Insights & Reports**: AI-generated spending insights and reports
- **Web Dashboard**: View your finances on desktop via embedded REST API

## Screenshots

*Coming soon*

## Installation

### Prerequisites
- Android device running Android 8.0 (API 26) or higher
- Notification access permission for automatic transaction capture

### Download
Download the latest APK from the [Releases](../../releases) page.

### Build from Source
```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/RupeeLog.git
cd RupeeLog

# Build debug APK (requires Java 17)
./gradlew assembleDebug

# APK will be at: app/build/outputs/apk/debug/app-debug.apk
```

## Setup

1. Install the APK on your Android device
2. Grant notification access when prompted
3. Set your monthly budget in Settings
4. (Optional) Configure Gemini API key for cloud AI features
5. (Optional) Download Gemma 3n model for local AI

## Tech Stack

- **Android**: Kotlin, Jetpack Compose, Material 3
- **Database**: Room
- **Dependency Injection**: Hilt
- **AI**: MediaPipe LLM Inference (Gemma), Google Generative AI SDK (Gemini)
- **HTTP Server**: Ktor with Netty
- **Dashboard**: React, TypeScript, Tailwind CSS, Recharts

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    RupeeLog Android App                     │
├─────────────────────────────────────────────────────────────┤
│  Notification Listener → Parser → Categorization Agent      │
│                              ↓                               │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │ Rule-Based │→ │ Local LLM  │→ │ Cloud API  │            │
│  └────────────┘  └────────────┘  └────────────┘            │
│                              ↓                               │
│                    Room Database                             │
│                              ↓                               │
│  ┌────────────────────────────────────────────┐            │
│  │           Ktor REST API (:8765)            │            │
│  └────────────────────────────────────────────┘            │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                  React Web Dashboard                         │
└─────────────────────────────────────────────────────────────┘
```

## API Endpoints

The app runs an embedded HTTP server on port 8765:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/transactions` | GET | List all transactions |
| `/api/transactions/summary` | GET | Get spending summary |
| `/api/categories` | GET | List categories with totals |
| `/api/budgets` | GET/POST | Manage budgets |
| `/api/reports/generate` | POST | Generate AI report |

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [Google Gemini](https://ai.google.dev/) for cloud AI capabilities
- [MediaPipe](https://developers.google.com/mediapipe) for on-device LLM inference
- [Ktor](https://ktor.io/) for the embedded HTTP server
