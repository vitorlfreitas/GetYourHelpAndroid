# GetYourHelp

BSCH-MD: **Developing an Android App**

Student Name: Vitor Freitas

Student Number: 3152612


## About

GetYourHelp is an Android marketplace for small everyday tasks. People who need a hand with something (walking a dog, doing the shopping, tidying a garden, helping with a move) publish a task, and people nearby who are willing to help apply for it. Tasks can be paid or offered as volunteering.

The app does not process money. Payments are agreed between the two people (cash or bank transfer) and the app only keeps a record of them.

## Features

- Account registration and sign-in with email and password (on beta version, later we move to third party providers), with clear error messages, a confirm-password field and a "Forgot password?" email link
- Publish a task as **paid** (with an amount and accepted payment methods) or as **volunteering**, with an optional date and time. The form checks every field and explains what is wrong under it
- Browse open tasks from other users, filtered by category, with live updates
- Apply for a task with a preferred payment method and an optional message to the requester; withdraw the application while it is pending, and apply again later
- Requesters see applications arrive live, and accept or decline each one. Only one helper can be accepted: accepting runs in a Firestore transaction, and the other pending applications are declined automatically
- Requesters confirm when a task is completed
- My Tasks has two tabs: **Requested** (Open / Ongoing / Completed) and **Helping** (Applied / Ongoing / Completed)
- After completion, the requester and the helper can review each other (1 to 5 stars and a comment)
- Report a task (unsafe, misleading, prohibited, wrong payment details, other)
- Wallet screen with the payment records for the tasks you were involved in, which can be marked as paid
- Profile screen where helpers choose the categories they are interested in
- Light and dark theme, following the phone's setting

## Technologies

|                | Technology                                                                   |
| -------------- | ---------------------------------------------------------------------------- |
| Language       | Kotlin                                                                       |
| UI             | Jetpack Compose, Material 3 with a custom theme (light and dark)             |
| Navigation     | Navigation Compose with type-safe routes (Kotlin Serialization)              |
| Architecture   | MVVM (ViewModels + repository) with Kotlin Coroutines, Flow and StateFlow    |
| Authentication | Firebase Authentication (email/password)                                     |
| Database       | Cloud Firestore with live listeners, protected by security rules             |
| Testing        | JUnit 4 unit tests                                                           |
| Build          | Gradle (Kotlin DSL), Android Gradle Plugin                                   |

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

### 4. Run the unit tests

```bash
./gradlew :app:testDebugUnitTest
```

The tests (in `app/src/test`) cover the form validation, the sign-in error messages, how My Tasks groups tasks, and the date and time conversions of the task scheduler.

## How to use

The bottom navigation has four tabs: **Browse**, **My Tasks**, **Wallet** and **Profile**. Tapping a task opens its details page. **Sign out** is in the top bar of the Profile tab.

### As a requester

1. On **Browse**, tap **Post a task**. Fill in the title, description, category and location, and optionally pick a date and time.
2. Choose **Paid task** (enter an amount and tick Cash and/or Bank transfer) or **Volunteer**, then publish. The app opens the new task.
3. Applications appear on the task's page as soon as helpers apply. Tap **Accept** on one (the others are declined) or **Decline** any of them.
4. The task moves to **My Tasks > Requested > Ongoing**. When the job is done, open it and tap **Confirm task completed**.
5. Leave a review for the helper.

### As a helper

1. Go to **Profile** and choose the categories you are interested in.
2. Find a task in **Browse** and open it.
3. Pick your preferred payment method, optionally write a message to the requester, and tap **Apply for this task**. You can **withdraw** while it is pending.
4. Follow it under **My Tasks > Helping**. Once you are accepted it moves to **Ongoing**.
5. After the requester confirms completion, leave a review.

For paid tasks, a payment record is created when the requester accepts a helper. Both people can see it under **Wallet** and mark it as paid once the money has changed hands.

### Testing with two devices

With Firebase connected, the easiest way to test is with two emulators (or an emulator and a phone):

1. Device A registers and publishes a paid task.
2. Device B registers, finds the task in Browse and applies with a message. Device A sees the application appear on the task's page without refreshing.
3. Device A accepts the application. Both devices now show the task as **Ongoing**.
4. Device A confirms completion, and both users leave a review.
5. Both users check the payment record in Wallet.

## Project structure

```text
GetYourHelpAndroid/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── res/            app icon, font, window theme and colours
│       │   └── java/com/vitorfreitas/getyourhelp/
│       │       ├── GetYourHelpApplication.kt
│       │       ├── MainActivity.kt
│       │       ├── AppViewModel.kt
│       │       ├── data/
│       │       │   ├── Models.kt
│       │       │   ├── MarketplaceRepository.kt
│       │       │   └── ErrorMessages.kt
│       │       ├── validation/     AuthValidator, TaskValidator
│       │       └── ui/
│       │           ├── GetYourHelpApp.kt
│       │           ├── UserMessenger.kt
│       │           ├── Formatting.kt
│       │           ├── theme/        colours, typography, shapes
│       │           ├── navigation/   Routes, MainScaffold, AppNavHost
│       │           ├── components/   TaskCard, status chips, empty state, ...
│       │           ├── auth/         LoginScreen
│       │           ├── browse/       BrowseScreen
│       │           ├── create/       CreateTaskScreen, CreateTaskViewModel, date and time picker
│       │           ├── mytasks/      MyTasksScreen and its grouping logic
│       │           ├── taskdetail/   TaskDetailScreen, TaskDetailViewModel, report and review dialogs
│       │           ├── wallet/       WalletScreen
│       │           └── profile/      ProfileScreen
│       └── test/                   unit tests
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── firebase.json
└── firestore.rules
```

| File / folder                       | Purpose                                                                                                                |
| ----------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `GetYourHelpApplication.kt`         | Creates the `AppContainer`: the one repository and message queue shared by all ViewModels                             |
| `MainActivity.kt`                   | Entry point: edge-to-edge display, the theme, and `GetYourHelpApp`                                                     |
| `AppViewModel.kt`                   | Data shared by all screens: the session, the live task list, the user's applications, wallet and profile              |
| `data/Models.kt`                    | Enums for statuses, task types, categories and payment methods, and the data classes saved in Firestore                |
| `data/MarketplaceRepository.kt`     | Reads and writes data with Firebase Authentication and Cloud Firestore                                                 |
| `data/ErrorMessages.kt`             | Turns Firebase errors into short, friendly messages                                                                    |
| `validation/`                       | Form validation rules, in plain Kotlin so they can be unit tested                                                      |
| `ui/GetYourHelpApp.kt`              | Root composable: loading spinner, login screen or the signed-in app, and the snackbar messages                        |
| `ui/theme/`                         | Brand colours (light and dark), the Plus Jakarta Sans font and shapes                                                  |
| `ui/navigation/`                    | Routes (one class per screen), the bottom navigation bar, and the `NavHost` that connects screens to their ViewModels |
| `ui/components/`                    | Small composables shared by several screens                                                                            |
| `ui/<feature>/`                     | One folder per screen; screens with their own logic also have their own ViewModel                                      |
| `firestore.rules`                   | Firestore security rules                                                                                               |
| `firebase.json`                     | Firebase CLI configuration pointing to the rules file                                                                  |

## Security

Access control is done by the Firestore rules, not by the UI. In short:

- Users can only read and edit their own profile.
- Any signed-in user can read tasks, but only the owner can create or delete one (and only delete it while it is still open).
- A task's status can only move forward (Open → Ongoing → Completed), and it can only become Ongoing in the same write that accepts that helper's application.
- A helper can apply only to open tasks that belong to someone else, and only once per task: the application's document id is `<taskId>_<helperId>`.
- Applications can be read by the helper who sent them and by the task owner. The owner can only accept or decline a pending application; the helper can only withdraw it, or apply again after withdrawing.
- Payment records are visible only to the payer and the payee, and only their status can be changed.
- Reviews can only be written by the requester or the assigned helper of a completed task, about the other person, with a rating from 1 to 5.

Passwords are handled by Firebase Authentication and are never stored in Firestore.

## Credits

- Font: [Plus Jakarta Sans](https://fonts.google.com/specimen/Plus+Jakarta+Sans), SIL Open Font License 1.1.
- App icon and in-app icons: [Material Symbols and Material Icons](https://fonts.google.com/icons) by Google, Apache License 2.0.
