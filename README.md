<h1 align="center">Jellyfin for Zidoo – maintained fork</h1>
<h3 align="center">A Jellyfin Android TV client optimized for Zidoo media players (Z9X, Z9X Pro, Z9S, …)</h3>

---

This is a maintained continuation of [Andy2244's Zidoo Edition](https://github.com/Andy2244/jellyfin-androidtv-zidoo)
(now archived), which is based on the [official Jellyfin Android TV client](https://github.com/jellyfin/jellyfin-androidtv).
Playback is handed to the **native Zidoo player**, so you keep Dolby Vision, HDR, HD audio passthrough, 3D MVC and disc menus.

## What's new in this fork

**Works with current Jellyfin servers (10.11 / 12.x)**
- Login fixed – current servers reject the old authorization headers (`X-Emby-Authorization`, `api_key`)
- Login no longer fails with "invalid password" because of changed server responses
- Obsolete `/Users/{id}/…` API routes are redirected to their replacements on Jellyfin 10.9+
- Jellyfin SDK updated to 1.4.7

**Playback (Zidoo player)**
- Auto-skip intro / credits / recaps / previews (Jellyfin media segments, needs e.g. the Intro Skipper plugin)
- Reliable "watched" status and auto-play next when an episode plays to the end
- Subtitles: side-car `.srt/.ass` files are passed to the player, online subtitle search uses the real title (#10)
- NFS direct path fixed, export root detected for Unraid / TrueNAS / Synology (#11)
- DVD/Blu-ray ISOs and disc folders play via direct path with disc menus (#4)
- `.strm` files play without probing them in another client first (#7)

**UI**
- Separate badges for hybrid HDR (Dolby Vision + HDR10 / HDR10+ / HLG, DV EL) and 3D (MVC / SBS / TAB)
- Seasons / Series navigation rows on season pages
- Episodes show their own image instead of the series fanart (#9)
- Text size setting – Normal / Large / Extra large (#8)
- Blurred backdrops (server-side, works on Android 9), media logos in the screensaver
- "Shuffle All" in music libraries
- Live TV guide crash fixed (#3)

## Install

1. Download the latest APK from [Releases](../../releases).
2. **First time only:** uninstall any previous Jellyfin Zidoo Edition (it was signed with a different key).
3. Install the APK on the Zidoo (e.g. via USB stick and the file manager, or `adb install`).

Later updates install over the previous version and keep your login and settings.

## Recommended setup

**Direct path (best quality, disc menus, side-car subtitles)** – Settings → Playback → *Use direct path*.
The Zidoo player then opens the file straight from your share instead of streaming it through Jellyfin.

- **SMB:** paths are sent as `smb://server/share/...`
- **NFS:** Jellyfin 10.9+ removed the "Shared network folder" library option. Use a path substitution in the server's
  `system.xml` (stop Jellyfin first). `/media` is the folder as Jellyfin sees it, `To` is the same folder as an NFS URL;
  the optional `/:` marks where the NFS export ends:

  ```xml
  <PathSubstitutions>
    <PathSubstitution>
      <From>/media</From>
      <To>nfs://192.168.1.10/mnt/user/media/:</To>
    </PathSubstitution>
  </PathSubstitutions>
  ```

**3D badges** – Jellyfin only knows a movie is 3D when the file name contains a tag (`Movie (2012) 3D.MVC.mkv`,
`.3D.HSBS.`) or the NFO has `<format3d>MVC</format3d>`.

## Building

- Android Studio with **JDK 17** as Gradle JDK, then `gradlew assembleRelease`
- Release signing is read from `local.properties` (`STOREFILE`, `KEYALIAS`, `KEYPASSWORD`, `STOREPASSWORD`);
  without it the release build uses the debug key
- CI: the *Zidoo Build* workflow builds every push; pushing a tag `vX.Y.Z` creates a GitHub release with the signed APK.
  Signing uses the repository secrets `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`,
  `SIGNING_STORE_PASSWORD`

## Credits & license

Based on the work of [Andy2244](https://github.com/Andy2244/jellyfin-androidtv-zidoo) and the
[Jellyfin](https://github.com/jellyfin/jellyfin-androidtv) contributors. Licensed under the GPL-2.0, see [LICENSE](LICENSE).
