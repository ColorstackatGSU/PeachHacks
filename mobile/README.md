# PeachHacks Staff

The phone app staff use at the event. It checks hackers in at the front desk and
hands each one an NFC badge, records badge taps at meals and workshops, and lets
venue staff look up whose badge they are holding. It is a React Native app built
with Expo, for iOS and Android, and talks to the Spring Boot API in `../backend`.

The app is the main check-in tool. The Check-in screen on the admin site
(`../admin`) still works and is the fallback when a phone or its NFC reader fails.

A badge is an NFC card (NTAG215). The app only ever reads the card's UID. It never
writes to a card and never reads what is stored on it.

## The three areas

What a person sees depends on the role of the account they sign in with. They are
the same accounts as on the admin site.

| Area | Admin | Volunteer | Lookup |
| --- | --- | --- | --- |
| Check-in desk | yes | yes | no |
| Event taps | yes | yes | no |
| Lookup | yes | yes | yes |

- **Check-in desk.** Scan the hacker's ticket QR code, or search for their name
  if they do not have it. The app shows the name and school in large type:
  check them against a photo ID. Then press "ID checked — tap badge" and hold a
  blank badge to the phone. That one step links the badge to the person and
  records the general check-in; nothing is recorded before it. The app then shows
  which lanyard to hand over and goes back to scanning. Someone who is not
  accepted cannot be checked in until an organizer accepts them, and there is no
  override. "Check in without a badge" records the check-in alone, for when
  badges cannot be read. This area needs a connection.
- **Event taps.** Pick an event, press "Start tapping", and tap badges one after
  another. Each tap shows a result: checked in (with the name), already checked
  in (with when and by whom, which is how a second trip through the meal line
  shows up), not accepted, a revoked badge or an unknown badge. Without a
  connection taps are saved on the phone and synced later; see
  [Offline taps](#offline-taps).
- **Lookup.** Tap a badge to see the holder's name and school, whether they are
  accepted and whether they are checked in. A Lookup account sees only this
  screen. It needs a connection.

Sign-ins last 12 hours, so staff sign in once a day. The app remembers the email
address, never the password. "Forgot password?" opens `admin.peachhacks.com`,
because password links open on the admin site.

## Requirements

- Node 22.
- A phone with NFC. Android: any phone with NFC. iPhone: iPhone 7 or later.
- To build for Android on your own machine: Android Studio (Android SDK) and a
  JDK. This works on Windows, macOS and Linux.
- To build for iOS: a Mac with Xcode, or EAS cloud builds. Either way an Apple
  Developer Program membership is needed to install on an iPhone. An iOS build
  cannot be made locally on Windows.
- An Expo account for EAS builds (`npm install -g eas-cli`).

## Why not Expo Go

Expo Go contains a fixed set of native modules, and NFC is not one of them. This
app needs a development build: its own native app, built once, that then loads
the JavaScript from your laptop the way Expo Go would. A new development build is
only needed when native code changes (a dependency with native code is added or
upgraded, or `app.json` changes); JavaScript changes reload as usual.

## Run it

```sh
cd mobile
npm install
```

Make a development build and install it on a device, one of:

```sh
# Android, built on this machine. The phone is connected by USB with USB debugging on.
npx expo run:android

# iOS, built on a Mac with Xcode. The iPhone is connected by cable.
npx expo run:ios --device

# Either platform, built by EAS in the cloud (see "EAS setup" first).
eas build --profile development --platform android
eas build --profile development --platform ios
```

`expo run:*` generates the `android/` and `ios/` folders. They are build output
and are ignored by git; `app.json` is the source of truth.

Then start the bundler and open the app on the phone:

```sh
npm start          # expo start --dev-client
```

Other scripts:

```sh
npm run lint          # ESLint
npm test              # Jest: the offline queue and the UID and ticket helpers
npm run build:check   # bundles the JavaScript for Android and iOS into dist/
npm run doctor        # expo-doctor
```

`npm run build:check` needs no Android or iOS toolchain. It proves the app
bundles; it does not compile native code. Pull requests into `main` run lint, the
tests and the bundle check (`.github/workflows/mobile-checks.yml`).

## Environment variables

Copy `.env.example` to `.env.local` to override. `.env*` files are ignored by git.

| Variable | Purpose | Default |
| --- | --- | --- |
| `EXPO_PUBLIC_API_BASE_URL` | Base URL of the API | `https://api.peachhacks.com` in a release build. In a development build: `http://10.0.2.2:8080` on Android, `http://localhost:8080` on iOS |

`EXPO_PUBLIC_*` values are put into the JavaScript when it is bundled. After
changing one, restart `npm start` (add `-- --clear` if the old value sticks). For
an EAS build, set it in the build profile's `env` in `eas.json` or in the EAS
environment for the project. It is a URL, not a secret.

Every request carries `X-PeachHacks-Client: staff-app`. Organizers can restrict
volunteers' web check-in to this app in the admin Settings screen; the API uses
that header to tell the two apart. It steers volunteers to the app. It is not a
security boundary, since a header can be sent by anything.

### Pointing a phone at a backend on your laptop

Start the backend with its `local` profile (see `../backend`). The development
defaults only suit an emulator or simulator: `10.0.2.2` is the Android emulator's
name for the machine it runs on, and the iOS simulator shares the Mac's network.

On a physical phone, `localhost` is the phone itself, so the default does not
work. Either:

- Put the phone and the laptop on the same Wi-Fi, find the laptop's address
  (`ipconfig` on Windows, `ipconfig getifaddr en0` on macOS) and set
  `EXPO_PUBLIC_API_BASE_URL=http://<that address>:8080` in `.env.local`. The
  laptop's firewall has to allow incoming connections on port 8080.
- Android over USB only: run `adb reverse tcp:8080 tcp:8080` and set
  `EXPO_PUBLIC_API_BASE_URL=http://localhost:8080`.

The sign-in screen of a development build shows the API address it is using.
Plain `http` to a laptop is for development builds. Preview and production builds
should use an `https` address.

To test the phone against real data without a laptop, set the variable to
`https://api.peachhacks.com`; taps and check-ins are then real.

## iOS NFC setup

Reading a card's UID on iOS needs the tag reader session (`NFCTagReaderSession`).
The other kind, the NDEF reader session, only reports NDEF messages and never the
UID, so it is not used. Background tag reading is not used either: the app reads
only while it is open and a scan sheet is showing.

What has to be in place, and where it comes from:

1. **The capability on the App ID.** The App ID `com.peachhacks.staff` needs
   "NFC Tag Reading" enabled. With EAS-managed credentials (the default),
   `eas build` registers the App ID and enables the capabilities it finds in the
   entitlements, so nothing is done by hand. To check or do it manually: Apple
   Developer site > Certificates, Identifiers & Profiles > Identifiers >
   `com.peachhacks.staff` > tick NFC Tag Reading > Save, then regenerate the
   provisioning profile.
2. **The entitlement.** `com.apple.developer.nfc.readersession.formats` must
   contain `TAG`. The `react-native-nfc-manager` config plugin in `app.json`
   writes it. `includeNdefEntitlement` is `false` there, so the entitlement holds
   `TAG` only; Apple has rejected builds that carry the `NDEF` format, and the app
   does not need it.
3. **The usage string.** `NFCReaderUsageDescription` in `Info.plist`, written by
   the same plugin from `nfcPermission`. Without it the app is terminated the
   first time it opens an NFC session.
4. **A rebuild.** All three are native configuration. Changing any of them needs
   a new build, not a reload.

To see what a build will contain without building:

```sh
npx expo config --type introspect
```

Look for the entitlement under `ios.entitlements`, and
`NFCReaderUsageDescription` and `NSCameraUsageDescription` under `ios.infoPlist`.

How reading behaves on an iPhone:

- NFC does not work in the iOS simulator. Use a real iPhone, iPhone 7 or later.
- Each read opens the system scan sheet, which covers the lower half of the
  screen. The app puts the result on the sheet as well as at the top of its own
  screen.
- iOS closes a session after about 60 seconds. On the Event taps screen the app
  keeps one session open across taps. When iOS times it out, the app opens a new
  one by itself as long as a badge was tapped in the last five minutes; otherwise
  it shows "Resume tapping". Pressing Cancel on the sheet also pauses it.
- The antenna is at the top edge of the phone. Hold the badge there.

## Android NFC notes

- `android.permission.NFC` comes from the same config plugin. The manifest marks
  the NFC hardware as not required, so the app installs on a phone without NFC
  and tells the user it cannot read badges.
- The app reads in reader mode, limited to NFC-A (the family NTAG215 belongs
  to), with the NDEF check skipped and the system sound off, so a tap is a UID
  read and nothing else. The app gives its own vibration.
- If NFC is switched off, the app says so and offers a button that opens the
  NFC settings. It checks again when you come back.
- There is no system sheet. The Event taps screen reads continuously until "Stop
  tapping" is pressed or the screen is left.
- The antenna is on the back of the phone, and its position differs between
  models. Find it once on each phone before the doors open.

On both platforms a card left resting on the phone is ignored for two seconds at
a time, so it is not counted again and again.

## EAS setup

These steps need the Expo account that will own the project and, for iOS, the
Apple Developer account. They have not been run; `app.json` deliberately has no
EAS project id yet.

```sh
npm install -g eas-cli
eas login
eas init           # creates the project on Expo and writes extra.eas.projectId into app.json
```

Commit the `app.json` change `eas init` makes. `eas.json` defines the build
profiles:

| Profile | What it makes | For |
| --- | --- | --- |
| `development` | Development build, internal distribution | Day-to-day development on real phones |
| `development-simulator` | The same, for the iOS simulator | Screens that do not need NFC or a camera |
| `preview` | Release build, internal distribution (an APK on Android) | Handing a test build to staff |
| `production` | Store build, build number incremented automatically | TestFlight and Play |

Build numbers are kept by EAS (`appVersionSource: remote`), so they are not
edited in `app.json`.

For internal iOS builds (`development`, `preview`) each iPhone has to be
registered first, because they are signed for named devices:

```sh
eas device:create      # gives a link or QR code to open on each iPhone
eas build --profile development --platform ios
```

Never commit keystores, `.p8`, `.p12` or provisioning files. EAS stores
credentials on its servers; `mobile/.gitignore` ignores the local file types.

## Getting it onto staff phones

### iPhone: TestFlight

1. In App Store Connect, create the app record: My Apps > + > New App, platform
   iOS, bundle ID `com.peachhacks.staff`. The bundle ID appears in the list once
   the App ID exists, which the first `eas build` for iOS takes care of.
2. Build: `eas build --profile production --platform ios`.
3. Upload: `eas submit --profile production --platform ios`, and pick the build.
   It asks for the Apple ID and the app the first time.
4. Wait for the build to finish processing in App Store Connect > TestFlight.
5. Add testers:
   - **Internal testers** are people on the App Store Connect team (up to 100).
     They get the build as soon as it has processed, with no review.
   - **External testers** are anyone with an email address or the public link.
     The first build sent to external testers goes through Beta App Review, which
     can take a day or more and can come back with questions. Staff who are not on
     the Apple team are external testers, so submit well before the event.
6. Testers install TestFlight from the App Store and accept the invite.

Beta App Review needs a way to sign in. Give the reviewer a working account in
the review notes, and make sure it can do nothing harmful to real data.

A TestFlight build expires 90 days after upload.

### Android

- **APK, simplest.** `eas build --profile preview --platform android` produces
  an APK and a link to it. Staff open the link on the phone and install it; the
  phone asks them to allow installs from the browser the first time.
- **Play internal testing.** `eas build --profile production --platform android`
  makes an app bundle; upload it to an internal testing track in the Play Console
  (or `eas submit --platform android` once a service account is set up) and add
  testers by email. This needs a Google Play developer account.

## Offline taps

Event taps keep working without a connection. This is how:

- When a tap cannot reach the server (no connection, a timeout after 8 seconds,
  or a server error), the app saves the badge UID, the event and the time the
  card was read, and shows "Saved, will sync". It cannot show the name, because
  only the server knows whose badge it is.
- Saved taps are sent oldest first, one at a time: when the connection returns,
  when the app comes back to the front, after signing in, and on a timer that
  backs off from 2 seconds to a minute while the server stays unreachable.
  "Sync now" on the Event taps screen sends them immediately.
- Sending a tap twice is harmless. The server records one check-in per person
  per event and uses the saved time, as long as it is less than 72 hours old.
- The number waiting is shown on the Event taps tab, in a bar on the other
  screens, on the sign-in screen and in the sign-out confirmation.
- After a sync, "Saved taps" on the Event taps screen says what the taps turned
  out to be, for example "2 queued taps were unknown badges", and lists the ones
  that were not plain check-ins.
- A session that has ended (401) stops the sync and keeps every tap; they are
  sent after the next sign-in. An account that may not record taps (403) also
  stops it and keeps them.
- A tap the server will never accept (the event was deleted, or the UID is not
  valid) moves to a "Could not sync" list with the reason. It is not retried on
  its own. It can be retried by hand or removed.
- Signing out, closing the app and restarting the phone do not lose saved taps.
  The event list is saved on the phone too, so an event can be picked offline
  once the list has loaded at least once.

Limits to know about:

- **Double taps across phones.** Offline, the app warns when the same badge is
  tapped twice for the same event on the same phone ("Already tapped on this
  phone"). A second phone that is also offline cannot know. Those cases only
  show up after both phones sync, as "already checked in" in the sync summary.
- **Not accepted, revoked and unknown badges** are only found out at sync time,
  after the person has gone through.
- **The app has to be open to sync.** There is no background sync. Keep the app
  open until the waiting count reaches zero.
- **The saved taps live in the app's storage.** Uninstalling the app or clearing
  its data deletes any that have not synced.
- A tap that timed out may have reached the server anyway. It is saved and sent
  again, and then shows up in the summary as already checked in.
- The check-in desk and Lookup do not work offline. Use the admin site's
  check-in or wait for the connection.

The queue and its rules are in `src/lib/tapQueue.js`, which has no React Native
imports, and are covered by `src/lib/__tests__/tapQueue.test.js`.

## How it is put together

- `App.js`: sign-in gate and the tab bar, filtered by role.
- `src/api/client.js`: every API call, in the same shape as the admin site's
  client. A `401 UNAUTHORIZED` clears the session and returns to the sign-in
  screen; it does not touch saved taps.
- `src/nfc/index.js`: the only file that talks to `react-native-nfc-manager`.
  `readUid()` reads one badge; `startContinuous()` keeps reading.
- `src/lib/`: plain modules (queue, UID and ticket helpers, formatting).
- `src/state/`: the session (`Auth.js`) and the queue with its sync triggers
  (`Queue.js`).
- `src/screens/`: sign-in and the three areas.
- `src/dev/DevEntry.js`: development stand-ins. In a development build on a
  device without NFC (a simulator or emulator) a text field takes a typed UID,
  and the scan screen has a text field for a ticket code. The file is loaded
  behind `__DEV__`, so it is left out of release bundles.

The session token is kept in the device keychain or keystore through
`expo-secure-store`. Saved taps, the event list and the remembered email are in
AsyncStorage.

## Testing NFC on real devices

NFC cannot be tested in a simulator, an emulator or CI. Use a development or
preview build on a real phone, a backend you can sign in to, and a few blank
NTAG215 cards.

On-device checklist, to run on at least one iPhone and one Android phone:

1. Sign in as a volunteer. All three tabs show. Sign in as a Lookup account: only
   Lookup shows.
2. Check-in desk: scan an accepted hacker's ticket QR. Name and school show.
   Press "ID checked — tap badge" and tap a blank card. "Checked in" shows with
   the lanyard, and the app returns to scanning.
3. Tap the same card for the same person again: still a success.
4. Tap that card for a different person: "This badge already belongs to someone
   else". Try another card.
5. For someone who has a badge, tap a different blank card: "Replace lost
   badge?" appears. Replace it, then confirm the old card reads as no longer
   valid on Lookup.
6. Scan the ticket of someone who is not accepted, and a QR code that is not a
   ticket: both dead-end screens show.
7. Search by name, pick someone, and finish the check-in with a badge.
8. "Check in without a badge" checks someone in and asks first.
9. Event taps: pick an event and tap several badges in a row without touching
   the screen. Tap one twice: "ALREADY CHECKED IN" with the time and name.
10. Leave a card resting on the phone for ten seconds: it is counted once.
11. iPhone: leave the scan sheet open for over a minute, then tap a badge. Note
    whether the sheet reopened by itself and whether "Resume tapping" works.
12. Switch on airplane mode and tap three badges, one of them twice: "Saved,
    will sync" three times and "Already tapped on this phone" once. Close and
    reopen the app: the waiting count is still there. Switch airplane mode off:
    the count drops to zero and the summary appears.
13. With taps waiting, sign out and sign in again: they are still there and
    sync.
14. Android: switch NFC off. The app says so and the button opens settings.
15. Tap a card that is not a badge (a transit card, a bank card): it is either
    ignored or refused as an unknown badge, and reading carries on.
16. Lookup: tap a bound badge, a revoked badge and a blank card.
17. Deny the camera permission: the scan screen explains and Search still works.

## What has and has not been tested

Run on a Windows machine, with no phone, no cards and no Mac:

- `npm run lint`, `npm test`, `npx expo-doctor` and `npm run build:check` (the
  JavaScript bundles for Android and iOS) pass.
- `npx expo config --type introspect` shows the `TAG` entitlement, the NFC and
  camera usage strings, and the Android NFC and CAMERA permissions.

Not tested:

- No native build was made, for either platform, locally or on EAS. The app has
  never been installed or launched on a device, simulator or emulator, so no
  screen has been seen running.
- Nothing involving a physical card: reading a UID, the iOS scan sheet, keeping
  one iOS session open across taps, the automatic reopen after a timeout, Android
  reader mode, and the two-second repeat guard on a real reader.
- The camera and QR scanning on a device.
- Haptics, the keychain and keystore, and connection change detection on a
  device.
- The app against the real API. The calls follow the agreed contract, but the
  badge endpoints were written at the same time as the app and the two have not
  been run together.
- `eas init`, `eas build`, `eas submit`, TestFlight and Play distribution.

The app icon is still the Expo template's placeholder.
