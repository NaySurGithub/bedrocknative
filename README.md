# BedrockNative

Decompiled sources of `bedrocknative-0.1.0.jar`, a Fabric mod that makes Minecraft
Java speak the Bedrock protocol natively (RakNet / NetherNet) with Bedrock mechanics.

Recovered with [Vineflower](https://github.com/Vineflower/vineflower) for
reverse-engineering and security analysis.

## Layout (Fabric Loom)

| Path | Contents |
|------|----------|
| `src/main/java/net/bedrocknative` | Decompiled Java sources (~1000 files) |
| `src/main/resources/assets/bedrocknative/mappings` | Bedrock/Java data mappings (incl. Geyser) |
| `src/main/resources/*.json`, `*.mixins.json`, `*.accesswidener` | Mod and mixin configuration |
| `src/main/resources/bedrocknative/base/bedrock-samples.txt` | Index of Mojang assets to fetch (see below) |

## Building

```bash
./gradlew build          # fetches Mojang assets, then builds
./gradlew build -PbaseBundle=off   # build without the bundled base archive
```

`gradle.properties` holds the real versions: Minecraft `26.3` with **official Mojang
mappings** (Fabric publishes no Yarn for it), Fabric Loader `0.20.1`, Fabric API
`0.166.2+26.3`. The shaded libraries are wired in `build.gradle` with the versions
read from the jar's `pom.properties` (jackson, jose4j, aircompressor, nukkitx
natives, expiringmap, cloudburst math); the cloudburstmc protocol/netty/nbt,
MinecraftAuth and libdatachannel versions are the latest published and may need
pinning to the exact build the mod used. **A clean compile is still not guaranteed** —
decompiled sources routinely need manual fixes.

## Mojang assets are not stored here

The original jar bundled a 21 MB slice of Mojang's `bedrock-samples` resource pack
(textures/sounds, "personal use only"). That archive is **not** committed. Instead,
the `fetchBaseAssets` Gradle task rebuilds it at build time from the pinned commit of
`Mojang/bedrock-samples` on GitHub, driven by `bedrock-samples.txt`, verifying every
file against its SHA-1 and regenerating the sidecar the mod validates. The archive
lands in `build/` and the jar, never in git.

## Removed from this copy

- **Mojang assets** — the bundled `bedrock-samples` textures/sounds (fetched at build).
- **Native binaries** — `.so` / `.dll` / `.exe` / `.dylib` helpers.
- **Embedded secret** — a Discord webhook token that `ReportVault` carried for hidden
  telemetry (player name + Xbox gamertag + server, per join and on shutdown). The
  encrypted blob was stripped; `url()` returns `null`, which disables the report.

## Notes

- Server identity over NetherNet uses a self-signed ES384 token (the key lives in its
  own payload) pinned trust-on-first-use.
- The NetherNet transport is built on `libdatachannel`, a different stack from the
  WebRTC implementation genuine Bedrock clients use.

Original code is **All Rights Reserved** by the BedrockNative authors; kept here for
analysis only, not for redistribution.
