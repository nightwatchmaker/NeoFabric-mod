# NeoFabric

NeoFabric is an experimental loader-compatibility foundation for Minecraft Java Edition.

Current slice (0.2.0-dev, Minecraft 26.2 target)

- Loader-neutral `NeoFabricRuntime` and thread-safe event bus.
- Safe metadata inspection without loading mod classes.
- Detection of `fabric.mod.json`, `META-INF/neoforge.mods.toml`, and `META-INF/mods.toml`.
- Normalized Fabric, NeoForge, and Forge descriptors.
- Minecraft 26.2 target profile (`1.21.11` development coordinate, Java 25+).
- Fabric Loader 0.19.5+ bootstrap bundled into the universal `neofabric-loader` artifact.
- NeoForge 26.2 FML bootstrap bundled into the same universal artifact.
- Forge 26.2 FML bootstrap bundled into the same universal artifact.
- Deterministic 26.2 compatibility coordinator with duplicate-ID rejection and loader adapter selection.
- NeoForge/FancyModLoader loader-owned bootstrap compiled into a standalone loader artifact.
- NeoFabric Core is embedded in that loader artifact; no NeoFabric host-adapter mod is required.
- Controlled per-mod classloading with parent-first shared API namespaces and child-first implementation namespaces.
- Shared event bus supports assignable event types, deterministic Forge-style priorities, and cancellation short-circuiting.
- Embedded FML setup events are forwarded into the shared NeoFabric event bus; Fabric loader hooks provide lifecycle event forwarding.
- Fabric Loader fat jar embeds NeoFabric Core and the built-in lifecycle/game transformer hooks; its optional ProGuard distribution step currently warns on embedded Core and is not used for this development artifact.
- Fabric Minecraft game transformers inject `notifyServerReady(this)` and `notifyClientReady(this)` into supported server/client entrypoints.
- Official game classloader applies an ordered bytecode transformation pipeline before defining Minecraft/mod classes.
- No external dependencies for the core; the Fabric adapter compiles against the real Fabric Loader API.

This is not yet a Minecraft launcher or a binary-compatible implementation of the three ecosystems. The 26.2 target profile is now explicit, but the next substantial layers are classloading/bootstrap adapters, official-name mappings, lifecycle emulation, registry/event translators, and a Minecraft-version-specific integration test matrix. The scanner is an optimization and compatibility foundation, not a claim that arbitrary compiled mods already launch.

Build and run:

    cd ~/Projects/NeoFabric
    chmod +x build.sh
    ./build.sh

The build produces one universal loader artifact:

    build/libs/neofabric-loader-3.8.0-dev.jar

Fabric Loader development artifact:

    vendor/fabric-loader-src/build/libs/fabric-loader-0.19.5+local-fat.jar


    cd vendor/fancymodloader-src
    ./gradlew --no-daemon :loader:jar -x createChangelog -x checkLicenseMain -x javaImmaculateCheck

Installer sources:

    installer/install-neofabric.sh
    installer/install-neofabric.ps1
    installer/NeoFabric.iss
    installer/generate-official-profile.py

The shell installer has been tested in an isolated `.minecraft` directory. `NeoFabric.iss` is the Windows Inno Setup source for producing a real `.exe` on Windows. These currently stage the development loader under `.minecraft/neofabric`; they do not yet create an official Minecraft Launcher version profile because that requires the final Minecraft version JSON, libraries, mappings, and launcher bootstrap integration.


    NeoFabric Loader (top-level runtime)
      -> NeoFabric Core API and lifecycle
      -> NeoForge compatibility backend
      -> Forge compatibility backend
      -> Fabric compatibility backend
      -> Minecraft 26.2 version bridge

NeoFabric owns bootstrap, mod discovery, classloading policy, lifecycle ordering, and the common API. The three compatibility backends translate each ecosystem's metadata, events, registries, networking, and transformation expectations into NeoFabric. NeoFabric must not be implemented as a Fabric mod or as a feature inside Fabric Loader.
