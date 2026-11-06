# AetherShield

A minimal Android front-end for the [Aether](https://github.com/CluvexStudio/Aether) censorship-circumvention
core. AetherShield runs the official Aether binary as a local SOCKS5 proxy, routes the whole device
through it with a real `VpnService` TUN, and keeps the interface simple enough to use without
understanding any of that.

<p align="center">
  <b>Android 10+ (API 29) · arm64-v8a · Kotlin + Jetpack Compose</b>
</p>

---

## Features

- **Real VPN, not a proxy switcher** — traffic is routed through a `VpnService` TUN and forwarded to
  the core's SOCKS5 listener by `hev-socks5-tunnel`.
- **13 tunnel modes** — Smart, MASQUE, WireGuard, gool/WIW, MIM, gool classic, Tor-only, and the
  Tor/Psiphon layered variants of MASQUE, WireGuard and gool.
- **Resilient connection** — a watchdog checks the core process and pushes a real SOCKS5 `CONNECT`
  through the tunnel; a dropped tunnel is reported as *Reconnecting* and retried with exponential
  backoff instead of leaving a stale "Connected" on screen.
- **Live traffic** — upload/download speed and totals come from the forwarder's own byte counters
  (`TProxyGetStats`), not from an estimate.
- **Real latency** — an HTTPS request is made *through* the tunnel and timed with a monotonic clock.
  This is proxy latency, not ICMP; the UI says so.
- **Exit information** — the exit IP and country are read from Cloudflare's `/cdn-cgi/trace` through
  the tunnel, so the app never reports your home address.
- **Psiphon country selection** — for the three `+ Psiphon` modes only, limited to the 26 regions
  Psiphon actually serves (see below).
- **VPN sharing** — share the tunnel with the local network: an HTTP CONNECT proxy on **8080** and a
  SOCKS5 proxy on **1080**, with a live list of connected devices. No authentication, so use it on
  your own Wi-Fi or hotspot only.
- **Four themes** — follow the phone, light, dark, or pure-black AMOLED.
- **Update check** — the app asks GitHub Releases for the latest version about once a day and shows a
  notification plus an in-app row.

### Tunnel modes

| Mode | Flags |
|---|---|
| Smart | walks MASQUE → WireGuard → gool → MIM until one connects |
| MASQUE | `--masque` (`--h3` or `--h2`) |
| WireGuard | `--wg` |
| gool / WIW | `--gool` |
| MIM | `--mim` |
| gool classic | `--gool-classic` |
| Tor only | `--tor-only` |
| WireGuard + Tor / gool + Tor / MASQUE + Tor | `--wg\|--gool\|--masque --tor` |
| WireGuard + Psiphon / gool + Psiphon / MASQUE + Psiphon | `--wg\|--gool\|--masque --psiphon` |

Transports: **HTTP/3 (QUIC)** and **HTTP/2 (TCP)**. IP modes: IPv4, IPv6, dual stack.

### Supported Psiphon exit countries

AT, AU, BE, CA, CH, CZ, DE, DK, ES, FI, FR, GB, ID, IE, IN, IT, JP, LT, NL, NO, PL, RO, RS, SE, SG, US

That is the complete list; anything else is rejected before the core is started, because a region
Psiphon does not serve makes the tunnel fail and the app retry forever.

---

## Requirements

- Android 10 (API 29) or newer.
- **arm64-v8a** device. The bundled native binaries are 64-bit ARM only; there is no 32-bit, x86 or
  x86_64 build because no such Aether/Psiphon/HEV binaries ship with the project.

## Download

APKs are attached to [GitHub Releases](https://github.com/Aliz773/AetherShield/releases):

| Variant | Use |
|---|---|
| `app-universal-release.apk` | normal install, pick this if unsure |
| `app-arm64-v8a-release.apk` | same as universal for this project (arm64 is the only ABI) |

Release APKs are signed with the project key; debug builds are signed with the debug key.

## Build

```bash
git clone https://github.com/Aliz773/AetherShield.git
cd AetherShield
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # R8-minified release APK
./gradlew testDebugUnitTest      # unit tests
```

Requirements: JDK 21+, Android SDK with platform 37 and build-tools 36.

Release signing is read from the environment so no key ever lives in the repository:

```bash
export AETHER_KEYSTORE_PATH=/path/to/keystore.jks
export AETHER_KEYSTORE_PASSWORD=...
export AETHER_KEY_ALIAS=...
export AETHER_KEY_PASSWORD=...
```

Without those, `assembleRelease` still builds but produces an unsigned APK.

### Native core integration

Three prebuilt binaries live in `app/src/main/jniLibs/arm64-v8a/` and are run as processes, not
loaded as libraries:

| File | What it is |
|---|---|
| `libaether_cli.so` | the official Aether v2.4.0 CLI (renamed so Android extracts it) |
| `libpsiphon-tunnel-core.so` | Psiphon's companion client from the same release archive |
| `libhev-socks5-tunnel.so` | HEV's TUN → SOCKS5 forwarder, loaded through JNI |

They come from the official Aether v2.4.0 release archive
(`aether-android-arm64.tar.gz`, sha256 `02f1a1d6…2073c`); the `aether` binary inside is byte-identical
to `libaether_cli.so`. `TProxyService` must stay in package `hev.htproxy` because the HEV JNI looks it
up by name — `app/src/main/keepRules/rules.keep` enforces that under R8.

## Update system

`UpdateCheckWorker` (WorkManager, network-constrained, ~24 h) calls the GitHub Releases API, compares
the tag with the installed `versionName` using `VersionMath`, stores the result and posts a
notification. Android decides when the job actually runs, so this is a daily-ish check rather than a
guarantee. A manual "Check now" is available in **About**.

Only the *check* is automated: downloading and installing an APK is left to the browser / the user,
because installing requires the user's explicit approval and the release signing key does not exist
in this repository.

## Credits

- **App (AetherShield)** — developer [@Aliz773](https://github.com/Aliz773) · Telegram
  [@TS_PETROS313](https://t.me/TS_PETROS313)
- **Aether core** — [CluvexStudio/Aether](https://github.com/CluvexStudio/Aether), licensed AGPL-3.0
- **hev-socks5-tunnel** — [heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel), MIT
- **psiphon-tunnel-core** — GPL-3.0; license texts are bundled under `app/src/main/assets/licenses/`

AetherShield is an independent front-end. It is not endorsed by the Aether project.

## License

The application code follows the licence of the Aether project it embeds (AGPL-3.0). Bundling the
Aether and Psiphon binaries carries their own obligations — publish the corresponding source if you
distribute this APK.
