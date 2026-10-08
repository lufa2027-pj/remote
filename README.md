# VU Smart 2K (80V4) Android TV Remote Control App

A lightweight, native Android TV remote app crafted for the **VU Smart 2K 80V4 TV** (Model: *Smart 2K ATV4*, 4-core ARMv7 1.3GHz, ~1GB RAM, running **Android 9**).

It utilizes the native **Google Android TV Remote Service protocol v2** (`com.google.android.tv.remote.service`) over Wi-Fi, completely independent of ADB for everyday control.

---

## Hardware & Environment Specifications
- **Target TV**: VU Smart 2K 80V4 (Smart 2K ATV4)
- **OS**: Android 9 (API 28)
- **TV Service**: `com.google.android.tv.remote.service`
- **Control Transport**: TCP / TLS mutual authentication (mTLS) on port `6466`
- **Pairing Transport**: TCP / TLS on port `6467` with 6-digit on-screen PIN challenge
- **Discovery**: Wi-Fi mDNS service type `_androidtvremote2._tcp.` via Android `NsdManager`
- **App Stack**: Kotlin 2.0, Jetpack Compose (Material3), Coroutines, Zero heavy bloat libraries (minSdk 24, targetSdk 34)

---

## Project Structure
```
VuSmartTvRemote/
├── build.gradle.kts                 # Root Gradle build config
├── settings.gradle.kts              # Module repositories & includes
├── gradle.properties                # JVM & AndroidX optimization
├── gradle/
│   └── libs.versions.toml           # Version Catalog (Compose, AGP, Kotlin)
└── app/
    ├── build.gradle.kts             # App dependencies (Compose, Coroutines)
    ├── proguard-rules.pro           # ProGuard shrinking & crypto rules
    └── src/
        └── main/
            ├── AndroidManifest.xml  # Wi-Fi, Multicast, & Internet permissions
            ├── res/
            │   └── values/          # Strings, colors, & themes
            └── java/com/vu/tvremote/
                ├── MainActivity.kt               # Entry activity
                ├── data/
                │   ├── TvDevice.kt              # Device IP model
                │   └── RemoteState.kt           # UI & connection states
                ├── discovery/
                │   └── TvDiscoveryManager.kt    # Wi-Fi mDNS auto-discovery
                ├── protocol/
                │   ├── AndroidTvRemoteProtocol.kt # TLS socket, pairing, protobuf
                │   ├── CertificateHelper.kt      # X.509 client cert generator
                │   └── KeyCodes.kt               # Android TV KeyCodes
                ├── viewmodel/
                │   └── RemoteViewModel.kt       # State, vibration, key dispatch
                └── ui/
                    ├── RemoteScreen.kt          # Tactile Remote UI
                    ├── components/              # Pairing & IP Dialogs
                    └── theme/                   # TV Dark ergonomics
```

---

## How to Build & Install

### Step 1: Open in Android Studio
1. Open **Android Studio** (Hedgehog, Iguana, Koala, or Ladybug).
2. Click **File → Open…** and select the unzipped `VuSmartTvRemote` folder.
3. Allow Gradle to sync. (It will download Compose BOM and AndroidX dependencies).

### Step 2: Build the APK
1. In Android Studio, go to the top menu: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
2. The output APK will be generated at:
   `app/build/outputs/apk/debug/app-debug.apk`

### Step 3: Install on your Phone
Connect your Android phone to your PC via USB and run:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Or simply click the green **Run (▶)** button in Android Studio with your phone selected.

---

## How to Pair with your VU Smart 2K TV

1. **Connect to Same Wi-Fi**:
   Ensure both your phone and the VU TV are connected to the same Wi-Fi router (2.4 GHz or 5 GHz).
2. **Launch the App**:
   Open **VU TV Remote** on your phone.
3. **Auto-Discovery**:
   The app will automatically discover your VU TV via mDNS.
   *(If your Wi-Fi router isolates mDNS packets, tap the Ethernet/Settings icon at the top right and enter the TV's IP address directly, e.g. `192.168.1.105`)*.
4. **First-Time Pairing**:
   - The app initiates a secure pairing request to `com.google.android.tv.remote.service`.
   - Your VU TV screen will display a **6-character PIN code**.
   - Type this code into the prompt dialog on your phone and press **Pair TV**.
5. **Ready to Control!**
   - The connection indicator turns **Green**.
   - Use the D-Pad, OK button, Home, Back, Volume +/-, Mute, Power, and Play/Pause seamlessly!

---

## Helpful ADB Debugging Commands (for development)
If you want to verify TV status over ADB over USB:
```bash
# Check if the Google TV Remote service is active on the TV:
adb shell dumpsys package com.google.android.tv.remote.service

# Check TV IP address on Wi-Fi:
adb shell ip addr show wlan0

# Test TV key input directly via ADB (development only):
adb shell input keyevent 19   # UP
adb shell input keyevent 20   # DOWN
adb shell input keyevent 23   # OK / CENTER
adb shell input keyevent 4    # BACK
adb shell input keyevent 3    # HOME
```
