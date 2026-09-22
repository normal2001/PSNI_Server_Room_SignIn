# PSNI Server Room Sign-In Android App

A simple Kotlin/Android application for server-room visitor sign-in/sign-out tracking using a local SQLite database and best-effort CSV exports to an SMB fileshare using **SMBJ 0.13.0**.
It is intended to be installed on an Android 16 tablet in kiosk mode (app pinning).

## Installation

The debug build signed installer package is usually named `app-debug.apk`.

1. On the device, download the installer package from a network source or shared with the device via a USB connection.
2. Open the installer package
3. Since the package is not coming from Google Play Store, you may need to select "Allow from this source" in the Download Manager settings.
4. Additional pop-up warnings may need to be accepted to allow installation from untrusted sources.

## CSV exports to SMB fileshare (optional)

SMB requirements:

- SMB2/SMB3-capable server reachable from the Android device.
- TCP 445 reachable from the Android network to the server.
- Configured SMB account has permission to create directories and overwrite the CSV in the configured share/path.
- The app has Android `INTERNET` permission (already declared).

After every successful database **sign-in**, **sign-out**, or **admin edit**, the app queues a background write to a CSV file (`PSNI_Server_Room_Sign-In_Sheet_YYYY.csv`) on an SMB fileshare, if enabled. If not enabled, the records are only visible within the application. The CSV exporter rebuilds the entire current-year CSV and overwrites it. This ensures that any records are not missing if the CSV export option was temporarily disabled or fails to be written. The 'YYYY' in the filename represents the current year, so this file will rollover to a new file name each year.

CSV columns are exactly:

`sign_in_datetime,sign_out_datetime,first_name,last_name,reason`

`reason` contains the reason for entering the server room. Missing values are exported as empty fields.

SQLite is the local source of truth. If SMB is unavailable, authentication fails, or the remote file cannot be written, the local database transaction stays committed and the SMB problem is written to `psni_server_room_sign_in_error.txt`.

The Fileshare tab's **Export CSV** button copies the current yearly CSV in the
share root to a timestamped snapshot such as:

`PSNI_Server_Room_Sign-In_Sheet_2026-09-04-134205_export.csv`

If the yearly CSV does not exist, the app first creates it from the current-year
SQLite records (or just the CSV header if there are no records), then makes the
timestamped copy. An existing yearly CSV is copied as-is. The page reports whether
that copy succeeded or failed.

## Slack notifications (optional)

Configure a Slack bot token and a channel name or ID in the Admin tab, then enable
Slack notifications. The bot needs the `chat:write` OAuth scope and access to the
configured channel; invite the bot to that channel before testing.

Each successful sign-in posts the visitor's name, sign-in time, and reason to the
configured channel. Delivery always uses the channel. No recipient selection,
Slack user group, user cache, or direct-message permissions are needed.
Previously saved individual-delivery settings are discarded on startup.

Slack runs in the background. Delivery failures are written to the error log and
never roll back a successful local sign-in. Notification attempts, successes,
and skips when disabled are recorded in the audit log.

## Audit logging

Successful database transaction types are appended to `psni_server_room_sign_in_audit.txt` with timestamps:

- `sign-in`
- `sign-out`
- `admin-edit`

When signing out users from the Admin page using the "Sign Out" button, this is audited as `sign-out` 
because it performs the same transaction type.


## Build/import

1. Clone the GitHub project into the renamed folder with `git clone https://github.com/normal2001/PSNI_SignIn.git PSNI_Server_Room_SignIn`, or extract a downloaded .zip file into a folder named `PSNI_Server_Room_SignIn`.
2. Open the `PSNI_Server_Room_SignIn` folder in Android Studio.
3. Let Android Studio sync Gradle and download dependencies from Google Maven/Maven Central.
4. Install Android SDK 36 if Android Studio requests it.
5. Use JDK 17.
6. Set the SMB values in `AppConfig_test.properties` or from the Fileshare admin tab before testing SMB export.
7. Build and run on Android 8.0 (API 26) or newer.

The project specifies Android Gradle Plugin 9.2.1, Kotlin 2.3.21, Gradle 9.4.1, compile/target SDK 36, and SMBJ 0.13.0.

> The standard `gradle-wrapper.jar` binary is intentionally not included in this generated source archive. Android Studio can import/sync the project directly. If you want command-line `./gradlew` usage, generate/add the wrapper from Android Studio or a local Gradle installation.

## Important configuration

Deployment defaults are defined in `app/build.gradle.kts` and exposed through
`app/src/main/java/com/example/psnisignin/AppConfig.kt`.

### Debugging using local test properties:

1. Edit `AppConfig_test.properties` in the project root. On a fresh clone, copy
   `AppConfig_test.example.properties` to that name first.
2. Use the exact keys shown in the example, with one `KEY=value` per line. All
   administrator settings, the reason choices, and `useMockData` are supported.
3. Sync Gradle and rebuild/run the **debug** variant after making changes.

This `AppConfig_test.properties` file is ignored by Git so your custom settings
remain local only.

Debug setting precedence: 
  1. any settings saved through the admin screen
  2. the `AppConfig_test.properties` settings
  3. the app defaults. 

The `AppConfig_test.properties` settings work as initial values for debugging, but 
any changes saved inside the app take priority and survive app restarts and normal
rebuilds/reinstalls that retain app data. The `REASON_OPTIONS` and `useMockData` are
build settings without saved admin overrides. Changing local properties requires
rebuilding and installing the app; restarting the emulator app alone does not update
it or erase saved settings. 

Release builds ignore the local properties file entirely. Release builds and debug
builds without local overrides use empty Slack connection credentials and disabled
SMB fileshare exports by default. The default admin passcode remains `1234`.
Previously saved settings take priority in both debug and release builds.

Properties are read as UTF-8. Do not surround values with quotes. Use `\\` for a
literal backslash and `\n` for a newline. Separate `REASON_OPTIONS` with `|`.
Booleans must
be `true` or `false`; unknown keys or invalid values cause a debug build error.
`useMockData` only exposes a flag; the app currently has no mock-data implementation.
 
Local credentials are excluded from source control, but are included in your debug
APK. If credentials previously committed to Git were real, rotate them; ignoring a
file does not remove credentials from earlier commits.

Default reasons are `Servers/Network`, `Utilities maintenance`, `Inspection/Audit`,
and `Emergency repairs`.

Default local filenames are:

- `DATABASE_PATH=psni_server_room_sign_in.db`
- `AUDIT_LOG_PATH=psni_server_room_sign_in_audit.txt`
- `ERROR_LOG_PATH=psni_server_room_sign_in_error.txt`

Saved administrator path overrides still take precedence. Selecting a new database
filename opens or creates that database; it does not migrate the old database.
The removed `SLACK_NOTIFICATION_TARGET` and `SLACK_USER_GROUP` keys should be
removed from local test properties.

### Android local-path behavior

If the SQLite database file cannot be opened at startup, the home page displays a
warning and disables check-in/check-out. The Settings button and admin passcode 
remain usable; login opens Application settings directly. Visitor records and CSV
export are unavailable until database access is restored. Save a corrected 
`DATABASE_PATH` to retry immediately. Failed attempts keep settings accessible. A 
successful retry opens or creates the database and re-enables check-in/check-out
when you return Home. Existing records are not copied from the old database. Changing
the path while a working database is already open still requires a full app restart.

Android does not provide a dependable writable process working directory. The app
interprets relative paths such as `./psni_server_room_sign_in_audit.txt`, `./psni_server_room_sign_in_error.txt`,
and `./psni_server_room_sign_in.db` relative to the app's private internal files directory. Absolute
paths are accepted by the resolver, but Android storage/scoped-storage permissions
still apply.

In the Admin screen's Application tab, the **View** button beside `AUDIT_LOG_PATH` or
`ERROR_LOG_PATH` opens that log in the default text-viewing app (or Android's app
picker when no default is selected). Save edited paths first. The viewer receives
temporary read-only access to the selected log. Missing or unreadable logs and a
missing text viewer are reported in the tab.

## Data model

SQLite table: `sign_in_records`

- `id` - auto-incrementing primary key
- `first_name`
- `last_name`
- `sign_in_datetime`
- `sign_out_datetime` - NULL while the person remains signed in
- `reason` - reason for entering the server room

Older databases may still contain an unused `visiting` column and Slack user-cache
table. These are left intact to preserve historical data; new records, edits,
CSV exports, and audit entries do not use them.

Timestamp format: `yyyy-MM-dd HH:mm:ss`

## Security notes

This project follows the requested source-level configurable passcode and SMB credentials. For a production deployment, hard-coded secrets are not strong secret storage: APK contents can be inspected. Prefer managed configuration and Android Keystore-backed secret storage if the application will be distributed beyond a controlled device fleet.

The 4-digit passcode is a convenience gate, not strong authentication. Admin Activities are marked `android:exported="false"`, reducing direct launch exposure from other apps.
