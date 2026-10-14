# PeachHacks Staff

The phone app staff use at the event. At Arrivals it checks hackers in and hands
each one an NFC badge; at meals and workshops it records badge taps; and anyone,
venue staff included, can check whose badge they are holding. It is a React Native app built
with Expo, for iOS and Android, and talks to the Spring Boot API in `../backend`.

The app is the main check-in tool. The Check-in screen on the admin site
(`../admin`) still works and is the fallback when a phone or its NFC reader fails.

The app needs a connection for everything it does. Nothing is saved on the phone
to be sent later; see [When the connection drops](#when-the-connection-drops).

A badge is an NFC card (NTAG215). The app only ever reads the card's UID. It never
writes to a card and never reads what is stored on it.

## The jobs

There is no tab bar. The app opens on a short home screen, "What are you doing?",
and each job opens full screen from there, with a back arrow to return. The app
remembers the last job and reopens straight into it. What a person can choose
depends on the role of the account they sign in with (the same accounts as on
the admin site):

| Job | Admin | Volunteer | Lookup |
| --- | --- | --- | --- |
| Arrivals | yes | yes | no |
| An event (meals, workshops) | yes | yes | no |
| Who's here | yes | yes | yes |
| Check a badge | yes | yes | yes |

A Lookup account (venue staff) gets a home screen with only Who's here and Check
a badge. The account name and Sign out are in a small sheet behind the round
button at the top of the home screen.

- **Arrivals** is the general check-in. Scan the hacker's ticket QR code (there is
  a light button on the camera), or "Search by name" for someone without it. The
  next screen shows the name very large and the school: check them against a
  photo ID, press "ID matches, tap badge" and hold a blank badge to the phone.
  That one step links the badge to the person and records the check-in; nothing
  is recorded before it. The app then names the lanyard to hand over, largest of
  all ("Georgia State University lanyard", "Staff lanyard", or the colour once
  organizers configure one), with the name under it, and goes back to the
  scanner after three seconds (tap the screen to go back at once). Someone who is
  not accepted cannot be checked in until an organizer accepts them, and there is
  no override. Someone who already has a badge gets the choice to replace a lost
  one. The "⋯" menu holds the rare actions: "Check in without a badge" (also
  offered on the screen after a badge read fails) and, for admins only, "Issue
  sponsor badge".
- **An event.** The event happening now (from half an hour before its start time
  to two hours after) is a large card on the home screen, two side by side if two
  overlap; with none happening, the next one is shown as "Up next". The rest are
  listed under it. Opening an event switches the reader on straight away; hold
  badges to the phone one after another. Each one gets a verdict in its own
  colour, with an icon and the person's name: Checked in, Already in (with the
  time of the first tap, which is how a second trip through the meal line shows
  up), Not accepted, Badge turned off, Unknown badge (both "Send them to
  Arrivals"), Sponsor badge OK, or Not saved. Successes clear back to Ready after
  1.5 seconds; everything else stays until the next badge. The header shows how
  many are in, and the last few taps are listed under the result. On an iPhone,
  when iOS closes the reader, "Tap to keep reading" starts it again.
- **Who's here** is read only: nothing on it checks anyone in or out. Search by
  name, and choose a scope: "Arrived" (everyone through Arrivals, newest first)
  or one event (the event happening now first; the general event is Arrived). The
  total for the scope shows under the chips ("214 arrived", "88 at Saturday
  lunch"). Each row has the name, a Staff tag when it applies, the school and the
  time they arrived or were checked in. Tapping someone shows when they arrived
  and the events they are checked in to, with times. It pages as you scroll and
  refreshes when pulled. It never shows emails or who checked anyone in.
- **Check a badge.** Hold a badge to the phone to see whose it is: the name, one
  status line (Checked in, Not checked in yet, Not accepted, Badge turned off,
  Unknown badge, or Sponsor badge) and the school. On Android it reads for as
  long as the screen is open; on an iPhone press "Check a badge" for each one.

Sponsor badges are not tied to a person. Tapped at an event they show "Sponsor
badge, OK" and record nothing. The API lets only a full admin issue them, so a
volunteer never sees "Issue sponsor badge", but still sees the sponsor result
when one is tapped.

People an organizer has marked as staff carry a small "Staff" tag next to their
name wherever the app shows it. The app never shows which staff member checked
someone in or issued a badge; that is on the admin site only.

Sign-ins last 30 days, so staff sign in once for the event. The app remembers the email
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
npm test              # Jest: the UID, ticket and API address helpers
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
- iOS closes a session after about 60 seconds. On an event screen the app keeps
  one session open across taps. When iOS times it out, the app opens a new one by
  itself as long as a badge was tapped in the last five minutes; otherwise it
  shows "Tap to keep reading". Pressing Cancel on the sheet also stops it.
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
- There is no system sheet. An event screen and Check a badge read continuously
  until the screen is left.
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

## When the connection drops

The app is online only. Every scan, tap and lookup is a request to the API, and
nothing is kept on the phone to be sent later.

What staff see:

- **Events.** A tap that does not get an answer (no connection, no answer
  within 5 seconds, or a server error) shows a cream "Not saved" panel with a red
  edge and an error vibration, clearly unlike the green "Checked in". The tap was not saved.
  Check the connection and tap the same badge again; it is read again straight
  away, without the usual two-second wait for a repeated card. On an iPhone the
  badge has to be lifted off the phone for a second first.
- **Arrivals.** Finding a person and saving a badge show the error with
  "Try again". After a badge tap, "Try again" sends the same badge again, so the
  card does not have to be tapped a second time. A failed "Check in without a
  badge" shows the error and can be pressed again.
- **Check a badge.** The panel says the badge could not be checked. Hold it again.
- **Every job** shows a red "No connection" banner from the moment a request
  fails to reach the server until a request gets through again. The app does not
  watch the network itself, so the banner appears after the first failed request
  and clears on the next one that works.
- **Opening the app.** If the server cannot be reached to confirm the sign-in,
  the app shows "No connection" with "Try again". The sign-in is kept, so no
  password is needed once the connection is back.

Things to know:

- A tap that timed out may have reached the server after all. Tapping the badge
  again then shows "Already in" with a first tap a moment ago. That person is
  checked in.
- The admin site's Check-in screen needs a connection too, so it is not a
  fallback for a network failure, only for a phone or reader that fails.

## How it is put together

- `App.js`: the sign-in gate, the "No connection" banner and the job switcher,
  which remembers the last job in the device's secure storage.
- `src/screens/`: `Home`, `Arrivals` (with `SponsorBadge`), `EventScreen`,
  `WhosHere`, `CheckBadge` and `SignIn`.
- `src/lib/attendance.js`: the Who's here scope chips, totals, empty-state wording,
  row times and paging, covered by `src/lib/__tests__/attendance.test.js`.
- `src/api/client.js`: every API call, in the same shape as the admin site's
  client. A `401 UNAUTHORIZED` clears the session and returns to the sign-in
  screen. It also tracks whether the last request reached the server, which is
  what the "No connection" banner shows.
- `src/nfc/index.js`: the only file that talks to `react-native-nfc-manager`.
  `readUid()` reads one badge; `startContinuous()` keeps reading.
- `src/theme.js`: every colour, font, size and spacing value. Components take
  their colours from here only.
- `src/components/Result.js`: the result panel (a result's colour, a 48 icon, the
  verdict, the name, one detail line; it slides in and, for "Already in", shakes)
  and the waiting phone with its rings.
- `src/lib/results.js`: what each API answer looks like (colour family, icon,
  words), the lanyard wording, and which events the home screen features. Plain
  functions, covered by `src/lib/__tests__/results.test.js`.
- `src/lib/feedback.js`: one haptic pattern per result family: a short pulse for
  a success, two for "already in", a hard one for a refusal, a refusal and a thud
  for "not saved". There are no sounds.
- `src/lib/` also has the UID and ticket helpers, formatting and the stored
  session.
- `src/state/`: who is signed in (`Auth.js`) and the event list (`events.js`).
- `src/dev/DevEntry.js`: development stand-ins. In a development build on a
  device without NFC (a simulator or emulator) the screens act as if a reader
  were listening and a small "Dev input" toggle opens a field for a typed UID;
  the scan screen has one for a ticket code. The file is loaded behind
  `__DEV__`, so it is left out of release bundles.

The session token, the remembered email and the last job are kept in the device
keychain or keystore through `expo-secure-store`. Nothing else is stored on the
phone. Arrivals and the event screen keep the screen awake while they are open.

### Look and assets

- The app is a calm navy tool. Peach is used for the one main button on a screen
  and small accents (the "Happening now" dot); colour fills large areas only for a
  result. Lists are plain rows with hairlines.
- Functional type is Open Sans (Regular, SemiBold, Bold). Agbalumo, the website's
  display face, is used only for the PeachHacks wordmark (sign-in and the home
  screen). Both come from `@expo-google-fonts/*` and are embedded in the native
  build by the `expo-font` config plugin, so there is no loading step. Android
  names an embedded font after its file and iOS after the name inside the file, so
  `src/theme.js` picks the family name per platform; never add `fontWeight` to
  them.
- Icons are the outline Material Design Icons from
  `@react-native-vector-icons/material-design-icons` (one set, its own config
  plugin). `@expo/vector-icons` is being retired by Expo and is not used.
- The brand images in `assets/brand/` (logo, moon, star, peach) and the app icon,
  Android adaptive icon layers and splash image were exported to PNG from the
  SVGs in `web/public/assets/`, so the app needs no SVG library. The app icon is
  the site's tag mark (`peachhacks_icon_tag.svg`) on navy.
- The splash screen comes from the `expo-splash-screen` plugin: the mark on
  navy. Splash changes only show in a release build.
- Fonts, icons, the splash and keep-awake are native: after changing any of them,
  make a new development build.

## Testing NFC on real devices

NFC cannot be tested in a simulator, an emulator or CI. Use a development or
preview build on a real phone, a backend you can sign in to, and a few blank
NTAG215 cards.

On-device checklist, to run on at least one iPhone and one Android phone:

1. Sign in as a volunteer: the home screen offers Arrivals, the events, Who's
   here and Check a badge, and the Arrivals "⋯" menu has no "Issue sponsor
   badge". Sign in as an admin: it is there. Sign in as a Lookup account: the home
   offers only Who's here and Check a badge, and Sign out is in its account
   sheet. Close and reopen the app: it comes back to the last job.
2. Arrivals: scan an accepted hacker's ticket QR. Name and school show. Press "ID
   matches, tap badge" and hold a blank card to the phone. The lanyard to hand
   over shows, then the scanner is back after three seconds.
3. Tap the same card for the same person again: still a success.
4. Tap that card for a different person: "This badge already belongs to someone
   else". Try another card.
5. For someone who has a badge, tap a different blank card: "Already has a badge"
   offers "Replace lost badge". Replace it, then check the old card shows "Badge
   turned off" in Check a badge.
6. Scan the ticket of someone who is not accepted, and a QR code that is not a
   ticket: both refusals show.
7. Search by name, pick someone, and finish the check-in with a badge.
8. "Check in without a badge" (the "⋯" menu) checks someone in and asks first.
9. Events: the event happening now is the large card on the home screen. Open it
   and hold several badges in a row without touching the screen. Tap one twice:
   "Already in" with the name and the time of the first tap (never who tapped it).
10. Leave a card resting on the phone for ten seconds: it is counted once.
11. iPhone: leave the scan sheet open for over a minute, then tap a badge. Note
    whether the sheet reopened by itself and whether "Tap to keep reading" works.
12. Switch on airplane mode and tap a badge: the "Not saved" panel shows with an
    error vibration, the "No connection" banner appears, and nothing is saved.
    Switch airplane mode off and tap the same card: it checks in and the banner
    goes.
13. In airplane mode, scan a ticket at Arrivals and hold a badge in Check a badge:
    both show an error and the banner. Close the app and open it again while still
    in airplane mode: "No connection" with "Try again" shows, and "Try again"
    works once the connection is back.
14. Android: switch NFC off. The app says so and the button opens settings.
15. Tap a card that is not a badge (a transit card, a bank card): it is either
    ignored or refused as an unknown badge, and reading carries on.
16. Check a badge: hold a bound badge, a turned-off badge and a blank card.
17. Deny the camera permission: the scan screen explains and Search still works.
    Try the light button on the camera.
18. As an admin, issue a sponsor badge from the Arrivals "⋯" menu, then tap it at
    an event ("Sponsor badge, OK", nothing counted) and in Check a badge ("Sponsor
    badge").
19. Who's here: "Arrived" lists the people checked in at Arrivals, newest first,
    with the total; switch to an event and back; search a name; tap someone to see
    their events; an event no one has been to says so. Nothing on the screen
    changes anything.
20. Mark a registration as staff on the admin site: the "Staff" tag shows next to
    the name at Arrivals, in search, in event results, in Who's here and in Check a
    badge.

## What has and has not been tested

Run on a Windows machine, with no phone, no cards and no Mac:

- `npm run lint`, `npm test`, `npx expo-doctor` and `npm run build:check` (the
  JavaScript bundles for Android and iOS) pass.
- `npx expo config --type introspect` shows the `TAG`-only entitlement, the NFC
  and camera usage strings, and the Android NFC and CAMERA permissions.
- A development build ran on an Android emulator. The first design pass ran against the local backend; the final screens were checked against a small stand-in API on the laptop shaped like the contract, because the local backend was down at the time. Every
  screen was used there: sign-in, the home screen with one, two and no events
  happening, Arrivals (scan, search, person, check-in with a typed UID, done, the
  "⋯" menu), the sponsor badge screen, every event result including "Not saved"
  with the emulator's network switched off and the retry once it was back, Check a
  badge, the account sheet, and the role differences. The emulator has no NFC, so
  UIDs came from the Dev input, and its camera shows a test scene, so tickets
  were typed too.

Not tested:

- Anything on an iPhone, and any native build for iOS (no Mac). EAS builds were
  not made.
- Nothing involving a physical card: reading a UID, the iOS scan sheet, keeping
  one iOS session open across taps, the automatic reopen after a timeout, Android
  reader mode, and the two-second repeat guard on a real reader.
- Scanning a real QR code with a camera.
- Haptics (the emulator does not vibrate), the keychain on iOS, and how text
  sizes behave with large accessibility font settings.
- How a failed request behaves on a real venue network, beyond switching the
  emulator's network off.
- `eas init`, `eas build`, `eas submit`, TestFlight and Play distribution.
