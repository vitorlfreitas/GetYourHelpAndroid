# GetYourHelp

BSCH-MD: **Developing an Android App**

Student Name: Vitor Freitas

Student Number: 3152612


## About

GetYourHelp is an Android marketplace for small everyday tasks. People who need a hand with something (walking a dog, doing the shopping, tidying a garden, helping with a move) publish a task, and people nearby who are willing to help apply for it. Tasks can be paid or offered as volunteering.

The app does not process money. Payments are agreed between the two people (cash or bank transfer) and the app only keeps a record of them.

## Features

- Account registration and sign-in with email and password (on beta version, later we move to third party providers)
- Publish a task as **paid** (with an amount and accepted payment methods) or as **volunteering**
- Browse open tasks from other users, filtered by category, with live updates
- Apply for a task and choose a preferred payment method from the ones the requester accepts
- Requesters see the applications for their tasks and can only accept one, other pending applications are declined automatically
- Requesters confirm when a task is completed
- After completion, the requester and the helper can review each other (1 to 5 stars and a comment)
- Report a task (unsafe, misleading, prohibited, wrong payment details, other)
- Wallet screen with the payment records for the tasks you were involved in, which can be marked as paid
- Profile screen where helpers choose the categories they are interested in

## Technologies

|                | Technology                                                         |
| -------------- | ------------------------------------------------------------------ |
| Language       | Kotlin                                                             |
| UI             | Jetpack Compose, Material 3                                        |
| Architecture   | MVVM (ViewModel + repository) with Kotlin Coroutines and StateFlow |
| Authentication | Firebase Authentication (email/password)                           |
| Database       | Cloud Firestore, protected by security rules (simplified)          |
| Build          | Gradle (Kotlin DSL), Android Gradle Plugin                         |

## Requirements

- Android Studio
- JDK 17+
- Android SDK 37+
- A device or emulator running Android 7.0 (API 24)
- A Firebase project

## Dependencies

Build tooling:

| Tool                   | Version |
| ---------------------- | ------- |
| Gradle                 | 9.8.2   |
| Android Gradle Plugin  | 9.4.1   |
| Kotlin                 | 2.4.21  |
| Google Services plugin | 4.5.0   |

Libraries (declared in [app/build.gradle.kts](app/build.gradle.kts)):

SDK levels: `minSdk 24`, `targetSdk 36`, `compileSdk 37`.

## Getting started

### 1. Clone and open

```bash
git clone https://github.com/vitorlfreitas/GetYourHelpAndroid.git
```

Open the project folder in Android Studio and let Gradle sync. Install any SDK components it asks for.

### 2. Connect Firebase

The app needs `app/google-services.json` to build. The file is git-ignored, so every clone needs its own copy.

1. Create a project in the [Firebase Console](https://console.firebase.google.com/).
2. Add an Android app with the package name `com.vitorfreitas.getyourhelp`.
3. Download `google-services.json` and place it in the `app/` folder.
4. In **Authentication > Sign-in method**, enable **Email/Password**.
5. In **Firestore Database**, create a database.
6. In **Firestore > Rules**, paste the contents of [firestore.rules](firestore.rules) and publish.

### 3. Run

Select the `app` configuration and run it on an emulator or a device.

To build from the command line:

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

## How to use

The bottom navigation has five tabs: **Browse**, **Create**, **My Tasks**, **Wallet** and **Profile**.

### As a requester

1. Go to **Create**, fill in the title, description, category and location.
2. Choose **Paid task** (enter an amount and tick Cash and/or Bank transfer) or **Volunteer**, then publish.
3. Open the task from **My Tasks** to see who applied, and tap **Accept applicant**.
4. When the job is done, open the task again and tap **Confirm task completed**.
5. Leave a review for the helper.

### As a helper

1. Go to **Profile** and choose the categories you are interested in.
2. Find a task in **Browse** and open it.
3. Pick your preferred payment method and tap **Apply for this task**.
4. Follow the task under **My Tasks**. Once you are accepted it shows as `ASSIGNED`.
5. After the requester confirms completion, leave a review.

For paid tasks, a payment record is created when the requester accepts a helper. Both people can see it under **Wallet** and mark it as paid once the money has changed hands.

### Testing with two devices

With Firebase connected, the easiest way to test is with two emulators (or an emulator and a phone):

1. Device A registers and publishes a paid task.
2. Device B registers, finds the task in Browse and applies.
3. Device A accepts the application; both devices now show the task as `ASSIGNED`.
4. Device A confirms completion, and both users leave a review.
5. Both users check the payment record in Wallet.

## Project structure

```text
GetYourHelpAndroid/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/values/styles.xml
│       └── java/com/vitorfreitas/getyourhelp/
│           ├── MainActivity.kt
│           ├── AppViewModel.kt
│           └── data/
│               ├── Models.kt
│               └── MarketplaceRepository.kt
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── firebase.json
└── firestore.rules
```

| File                            | Purpose                                                                                                                               |
| ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `MainActivity.kt`               | Entry point and all Compose screens: login, browse, create, my tasks, wallet, profile, and the task detail, report and review dialogs |
| `AppViewModel.kt`               | Holds the UI state (`MarketplaceState`) and exposes the actions the screens call                                                      |
| `data/Models.kt`                | Data classes: `AppUser`, `TaskItem`, `TaskApplication`, `PaymentRecord`, `TaskReport`, `ReviewRecord`                                 |
| `data/MarketplaceRepository.kt` | Reads and writes data with Firebase Authentication and Cloud Firestore                                                                |
| `firestore.rules`               | Firestore security rules                                                                                                              |
| `firebase.json`                 | Firebase CLI configuration pointing to the rules file                                                                                 |

## Security

Access control is done by the Firestore rules, not by the UI. In short:

- Users can only read and edit their own profile.
- Any signed-in user can read tasks, but only the owner can edit or delete one (and only delete it while it is still open).
- A helper can apply only to open tasks that belong to someone else.
- Applications can be read by the helper who sent them and by the task owner.
- Payment records are visible only to the payer and the payee, and only their status can be changed.
- Reviews can only be written by the requester or the assigned helper of a completed task, about the other person, with a rating from 1 to 5.

Passwords are handled by Firebase Authentication and are never stored in Firestore.
