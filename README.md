# PoseTrack - AI-Powered Workout Tracker

PoseTrack is a modern Android application that uses Artificial Intelligence and Computer Vision to transform your phone into a personal trainer. Using Google's ML Kit Pose Detection, the app tracks your movements in real-time, counts repetitions, and provides instant feedback on your exercise form.

## 🚀 Key Features

*   **AI Repetition Counting:** Automatically counts **Push-ups** and **Pull-ups** using advanced pose estimation.
*   **Real-time Form Feedback:** Notifies you if you are not fully in the frame or if your form needs adjustment.
*   **Adaptive Visibility:** Smart algorithms that work even when only your upper body is visible (useful for confined spaces).
*   **Movement Stabilization:** Implements Exponential Moving Average (EMA) filters to eliminate "jitter" and provide smooth tracking.
*   **Session Management:** Start, pause, and review your workouts before saving them.
*   **Manual Logging:** Use the app as a traditional workout diary by manually entering reps when the camera isn't used.
*   **Training History & Stats:** Track your progress over time with a built-in local database.
*   **Modern UI:** Beautifully crafted with Jetpack Compose following Material 3 design guidelines.

## 🛠 Tech Stack

*   **Language:** Kotlin
*   **UI Framework:** Jetpack Compose
*   **AI/ML:** Google ML Kit (Pose Detection)
*   **Camera:** CameraX API
*   **Dependency Injection:** Hilt (Dagger)
*   **Database:** Room Persistence Library
*   **Architecture:** MVVM (Model-View-ViewModel) with Clean Architecture principles.
*   **Navigation:** Jetpack Navigation Component

## 📂 Project Structure

- `camera/`: CameraX implementation and frame processing.
- `pose/`: ML Kit integration and pose result handling.
- `exercise/`: Core logic for movement analysis (Push-up & Pull-up algorithms).
- `db/`: Room database, entities, and DAOs for exercise persistence.
- `ui/`: Compose screens, themes, and custom drawing components (PoseOverlay).
- `viewmodel/`: Business logic and state management.

## 🔧 Installation & Setup

1.  Clone the repository:
    ```bash
    git clone https://github.com/your-username/PoseTrack-Android.git
    ```
2.  Open the project in **Android Studio (Ladybug or newer)**.
3.  Sync the project with Gradle files.
4.  Run the app on a physical device with a camera (API 24+ recommended).

## 💡 How It Works

The app captures video frames via **CameraX** and sends them to a background executor. **ML Kit** identifies 33 key body landmarks. Our custom **Exercise Analyzers** calculate joint angles (e.g., elbow angle) and monitor state transitions (e.g., "Down" to "Up") to count reps precisely while filtering out noise and minor shakes.

---

Built with ❤️ for better fitness
