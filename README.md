# GetYourHelp

BSCH-MD: **Developing an Android App**

Student Name: Vitor Freitas

Student Number: 3152612


## About

GetYourHelp is an Android marketplace for small everyday tasks. People who need a hand with something (walking a dog, doing the shopping, tidying a garden, helping with a move) publish a task, and people nearby who are willing to help apply for it. Tasks can be paid or offered as volunteering.

The app does not process money. Payments are agreed between the two people (cash or bank transfer) and the app only keeps a record of them.

## Features

- Account registration and sign-in with email and password, or with **Continue with Google**. Clear error messages, a confirm-password field and a "Forgot password?" email link
- Publish a task as **paid** (with an amount and accepted payment methods) or as **volunteering**, with an optional date and time. The form checks every field and explains what is wrong under it
- **Use my current location** (GPS) fills in the task's area, e.g. "Ranelagh, Dublin". Only an approximate position (about 100 m) is saved, never an exact address
- Browse open tasks from other users with live updates, filtered by category and **distance** (within 1 to 50 km) and sorted by **newest** or **nearest**. Each card shows how far away the task is ("1.2 km away")
- **Open in Maps** shows a task's area in Google Maps
- Apply for a task with a preferred payment method and an optional message to the requester; withdraw the application while it is pending, and apply again later
- Requesters see applications arrive live, and accept or decline each one. Only one helper can be accepted: accepting runs in a Firestore transaction, and the other pending applications are declined automatically
- Requesters can **edit** or **cancel** a task while it is open. Once someone has applied, the payment can no longer be changed
- A live **chat** between the requester and the accepted helper, which becomes read-only when the task is completed
- Two-step completion: the helper **marks the work as done**, then the requester **confirms** it (or says it is not finished yet)
- My Tasks has two tabs: **Requested** (Open / Ongoing / Completed / Cancelled) and **Helping** (Applied / Ongoing / Completed)
- After completion, the requester and the helper can review each other (1 to 5 stars and a comment)
- Report a task (unsafe, misleading, prohibited, wrong payment details, other), once per user
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
| Authentication | Firebase Authentication (email/password, and Google with Credential Manager) |
| Database       | Cloud Firestore with live listeners, protected by security rules             |
| Location       | Fused Location Provider (GPS) and Android's Geocoder, maps via a `geo:` link |
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
3. In **Authentication > Sign-in method**, enable **Email/Password** and **Google**.
4. In **Project settings > Your apps**, add the SHA-1 fingerprint of the key that signs the app. For debug builds, `./gradlew signingReport` prints it.
5. Download `google-services.json` (after steps 3 and 4, so it contains the Google sign-in client) and place it in the `app/` folder.
6. In **Firestore Database**, create a database.
7. In **Firestore > Rules**, paste the contents of [firestore.rules](firestore.rules) and publish.

Without step 4, or with an older `google-services.json`, the app still builds and email sign-in works, but "Continue with Google" explains that Google sign-in is not available yet.

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

The tests (in `app/src/test`) cover the form validation, the sign-in error messages, how My Tasks groups tasks, the date and time conversions of the task scheduler, distances and place names, the Browse filters and sorting, and how the chat groups messages by day.

## How to use

The bottom navigation has four tabs: **Browse**, **My Tasks**, **Wallet** and **Profile**. Tapping a task opens its details page. **Sign out** is in the top bar of the Profile tab.

### As a requester

1. On **Browse**, tap **Post a task**. Fill in the title, description and category. For the location, type a town or area, or tap **Use my current location**. Optionally pick a date and time.
2. Choose **Paid task** (enter an amount and tick Cash and/or Bank transfer) or **Volunteer**, then publish. The app opens the new task.
3. While the task is open you can **Edit task** or **Cancel task** from its page.
4. Applications appear on the task's page as soon as helpers apply. Tap **Accept** on one (the others are declined) or **Decline** any of them.
5. The task moves to **My Tasks > Requested > Ongoing**. Use **Message** to chat with the helper, e.g. to share the exact address.
6. When the helper marks the work as done, open the task and tap **Confirm completion** (or **Not finished yet**).
7. Leave a review for the helper.

### As a helper

1. Go to **Profile** and choose the categories you are interested in.
2. Find a task in **Browse**. Tap **Turn on location** to see how far away each task is, and to filter by distance or sort by nearest.
3. Open a task (**Open in Maps** shows where it is), pick your preferred payment method, optionally write a message to the requester, and tap **Apply for this task**. You can **withdraw** while it is pending.
4. Follow it under **My Tasks > Helping**. Once you are accepted it moves to **Ongoing**, and you can chat with the requester.
5. When you have finished, tap **Mark work as done**. After the requester confirms, leave a review.

For paid tasks, a payment record is created when the requester accepts a helper. Both people can see it under **Wallet** and mark it as paid once the money has changed hands.

### Testing with two devices

With Firebase connected, the easiest way to test is with two emulators (or an emulator and a phone). To test distances on an emulator, set its position in **Extended controls (⋯) > Location**, for example to Dublin.

1. Device A registers and publishes a paid task, using **Use my current location**.
2. Device B registers, finds the task in Browse (with its distance) and applies with a message. Device A sees the application appear on the task's page without refreshing.
3. Device A accepts the application. Both devices now show the task as **Ongoing**, and they chat about the details.
4. Device B marks the work as done, Device A confirms it, and both users leave a review. The chat is now read-only.
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
│       │       ├── auth/           GoogleSignInHelper (Credential Manager)
│       │       ├── location/       LocationService (GPS, Geocoder), distances, Maps link
│       │       ├── validation/     AuthValidator, TaskValidator, ChatValidator
│       │       └── ui/
│       │           ├── GetYourHelpApp.kt
│       │           ├── UserMessenger.kt
│       │           ├── Formatting.kt
│       │           ├── theme/        colours, typography, shapes
│       │           ├── navigation/   Routes, MainScaffold, AppNavHost
│       │           ├── components/   TaskCard, status chips, empty state, location permission, ...
│       │           ├── auth/         LoginScreen
│       │           ├── browse/       BrowseScreen and its filters
│       │           ├── create/       TaskFormScreen and TaskFormViewModel (post and edit), date and time picker
│       │           ├── mytasks/      MyTasksScreen and its grouping logic
│       │           ├── taskdetail/   TaskDetailScreen, TaskDetailViewModel, report and review dialogs
│       │           ├── chat/         ChatScreen, ChatViewModel
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
| `GetYourHelpApplication.kt`         | Creates the `AppContainer`: the repository, message queue, location service and Google sign-in shared by all ViewModels |
| `MainActivity.kt`                   | Entry point: edge-to-edge display, the theme, and `GetYourHelpApp`                                                     |
| `AppViewModel.kt`                   | Data shared by all screens: the session, the live task list, the user's applications, wallet and profile              |
| `data/Models.kt`                    | Enums for statuses, task types, categories and payment methods, and the data classes saved in Firestore                |
| `data/MarketplaceRepository.kt`     | Reads and writes data with Firebase Authentication and Cloud Firestore                                                 |
| `data/ErrorMessages.kt`             | Turns Firebase errors into short, friendly messages                                                                    |
| `auth/GoogleSignInHelper.kt`        | Shows the Google account picker (Credential Manager) and returns a Google ID token for Firebase                       |
| `location/`                         | Reads the GPS position, looks up place names, calculates distances and opens Google Maps                              |
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
- Any signed-in user can read tasks, but only the owner can create, edit or cancel one, and only while it is open. Editing cannot change the status, the owner or the helper.
- A task's status can only move forward (Open → Ongoing → Completed, or Open → Cancelled), and it can only become Ongoing in the same write that accepts that helper's application.
- Only the accepted helper can mark the work as done, and the owner can only confirm completion after that.
- A helper can apply only to open tasks that belong to someone else, and only once per task: the application's document id is `<taskId>_<helperId>`.
- Applications can be read by the helper who sent them and by the task owner. The owner can only accept or decline a pending application; the helper can only withdraw it, or apply again after withdrawing.
- A task's chat can only be read by the requester and the accepted helper. They can only write while the task is ongoing, as themselves, with the server's time, and messages cannot be edited or deleted.
- Each user can report a task only once (the report's document id is `<taskId>_<userId>`), and nobody can read other people's reports.
- Payment records are visible only to the payer and the payee, and only their status can be changed.
- Reviews can only be written by the requester or the assigned helper of a completed task, about the other person, with a rating from 1 to 5.

Passwords are handled by Firebase Authentication and are never stored in Firestore. The user's own location is only kept in memory to calculate distances; a task stores an approximate position (rounded to about 100 m) so it never reveals an exact address.

## Credits

- Font: [Plus Jakarta Sans](https://fonts.google.com/specimen/Plus+Jakarta+Sans), SIL Open Font License 1.1.
- App icon and in-app icons: [Material Symbols and Material Icons](https://fonts.google.com/icons) by Google, Apache License 2.0.
- The Google "G" logo on the sign-in button follows Google's [Sign in with Google branding guidelines](https://developers.google.com/identity/branding-guidelines).
