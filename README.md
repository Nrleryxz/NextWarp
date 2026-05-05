# NextWarp 🌀

NextWarp is a lightweight, beautiful warp system designed for Paper/Folia servers.
Light beautiful warp system compatible with Folia.

## Features

- Folia compatible (RegionScheduler / GlobalRegionScheduler)
- Actionbar teleport countdown (5 → 1)
- Cancel teleport on movement (configurable)
- Configurable sounds & particles (countdown + success/fail/cancel)
- Instant warps whitelist (`insta_tp`)
- Optimized for high player counts (cached messages/settings, O(1) warp lookup)

## Commands & Permissions

| Command | Description | Permission |
|---|---|---|
| `/warp <name>` | Teleport to a warp | `nextwarp.use` |
| `/warps` | List warps | `nextwarp.use` |
| `/setwarp <name>` | Create/update a warp | `nextwarp.admin` |
| `/delwarp <name>` | Delete a warp | `nextwarp.admin` |
| `/nextwarp reload` | Reload config | `nextwarp.admin` |

## Configuration

`config.yml`

```yml
settings:
  teleport-delay-seconds: 5
  cancel-on-move: true
  cancel-move-distance: 0.1

  countdown-sound: BLOCK_NOTE_BLOCK_PLING
  countdown-particle: PORTAL
  countdown-particle-count: 12

  teleport-sound: ENTITY_ENDERMAN_TELEPORT
  teleport-sound-volume: 0.6
  teleport-sound-pitch: 1.0
  teleport-success-particle: PORTAL
  teleport-success-particle-count: 30

  teleport-failed-sound: ENTITY_VILLAGER_NO
  teleport-cancelled-sound: BLOCK_NOTE_BLOCK_BASS

  warp-created-sound: BLOCK_NOTE_BLOCK_CHIME
  warp-deleted-sound: BLOCK_NOTE_BLOCK_HAT
  warp-not-found-sound: BLOCK_NOTE_BLOCK_BASS

  reload-sound: BLOCK_NOTE_BLOCK_BIT

messages:
  teleport:
    count: "&7ᴛᴇʟᴇᴘᴏʀᴛɪɴɢ: &5{countdown} &7sᴇᴄᴏɴᴅs"
    success: "&7sᴜᴄᴄᴇssғᴜʟʟʏ ᴛᴇʟᴇᴘᴏʀᴛᴇᴅ ᴛᴏ &5{warp}&7"
    failed: "&cᴛᴇʟᴇᴘᴏʀᴛ ғᴀɪʟᴇᴅ!"
    cancelled: "&cᴛᴇʟᴇᴘᴏʀᴛ ᴄᴀɴᴄᴇʟʟᴇᴅ!"

insta_tp:
  - Spawn

warps: {}
```

## Installation

1. Download `NextWarp-1.0.0.jar` from your build output.
2. Put it into your server `plugins/` folder.
3. Restart the server.

## Build

```bash
./gradlew clean build
```

The compiled JAR will be located in `build/libs/`..

## Author

- Nrleryx
