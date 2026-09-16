#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD="$ROOT/build"
rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/adapter-classes" "$BUILD/neoforge-classes" "$BUILD/forge-classes" "$BUILD/test-classes" "$BUILD/libs"
ASM_JAR="/root/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm/9.10.1/ada2141c0cc52ee8f5c48cd5fa4ce0e794f22236/asm-9.10.1.jar"
ASM_TREE_JAR="/root/.gradle/caches/modules-2/files-2.1/org.ow2.asm/asm-tree/9.10.1/e244332a17564c1d1572449399a842de35881be2/asm-tree-9.10.1.jar"
JSPECIFY_JAR="/home/nightwatchmaker/paper-flashback-26.2-test/libraries/org/jspecify/jspecify/1.0.0/jspecify-1.0.0.jar"
FASTUTIL_JAR="/root/.gradle/caches/modules-2/files-2.1/it.unimi.dsi/fastutil/8.5.18/a6cff377eecc19c2037bf31568a6d7106b50ba1f/fastutil-8.5.18.jar"
if [[ ! -f "$ASM_JAR" || ! -f "$ASM_TREE_JAR" || ! -f "$JSPECIFY_JAR" || ! -f "$FASTUTIL_JAR" ]]; then echo "ASM/JSpecify/FastUtil dependencies missing" >&2; exit 1; fi
find "$ROOT/src/main/java" -name '*.java' -print0 | xargs -0 javac --release 17 -cp "$ROOT/vendor/fml-loader-11.0.16.jar:$ROOT/vendor/neoforge-26.2.0.86.jar:$ASM_JAR:$ASM_TREE_JAR" -d "$BUILD/classes"
javac --release 17 -cp "$BUILD/classes:$ROOT/vendor/fml-loader-11.0.16.jar:$ROOT/vendor/neoforge-26.2.0.86.jar:$ROOT/vendor/fancymodloader-src/loader/build/libs/loader-11.0.16-neofabric.1.jar:$ASM_JAR:$ASM_TREE_JAR:/root/.gradle/caches/modules-2/files-2.1/net.fabricmc/sponge-mixin/0.17.1+mixin.0.8.7/1cca8837fa31d8cfbde11a391f108f2bda30bca4/sponge-mixin-0.17.1+mixin.0.8.7.jar" \
    -d "$BUILD/classes" \
    "$ROOT/vendor/fancymodloader-src/loader/src/main/java/net/neoforged/fml/neofabric/NeoFabricBootstrap.java" \
    "$ROOT/vendor/fancymodloader-src/loader/src/main/java/net/neoforged/fml/neofabric/NeoFabricMixinBridge.java"
javac --release 25 -cp "$BUILD/classes:$ROOT/vendor/fabric-loader-0.19.5.jar" -d "$BUILD/adapter-classes" \
    "$ROOT/src/adapter/java/org/neofabric/fabric/NeoFabricFabricAdapter.java" \
    "$ROOT/src/adapter/java/org/neofabric/fabric/FabricNativeRegistryBridge.java" \
    "$ROOT/src/adapter/java/net/minecraftforge/eventbus/api/IEventBus.java" \
    "$ROOT/src/adapter/java/net/minecraftforge/eventbus/api/Event.java" \
    "$ROOT/src/adapter/java/net/minecraftforge/eventbus/api/SubscribeEvent.java" \
    "$ROOT/src/adapter/java/net/minecraftforge/eventbus/api/Cancelable.java" \
    "$ROOT/src/adapter/java/net/minecraftforge/eventbus/api/EventPriority.java" \
    "$ROOT/src/adapter/java/net/minecraftforge/fml/common/Mod.java" \
    "$ROOT/src/adapter/java/net/minecraftforge/api/distmarker/Dist.java" \
    "$ROOT/src/adapter/java/net/minecraftforge/registries/RegistryObject.java" \
    "$ROOT/src/adapter/java/net/minecraftforge/registries/IForgeRegistry.java" \
    "$ROOT/src/adapter/java/net/minecraftforge/registries/ForgeRegistries.java" \
    "$ROOT/src/adapter/java/net/minecraftforge/registries/DeferredRegister.java" \
    "$ROOT/src/adapter/java/net/minecraftforge/common/MinecraftForge.java"
find "$ROOT/src/adapter/java" -path '*/neoforge/*.java' -print0 | xargs -0 javac --release 25 -cp "$BUILD/classes:$ROOT/vendor/fml-loader-11.0.16.jar:$ROOT/vendor/neoforge-26.2.0.86.jar:/root/.gradle/caches/modules-2/files-2.1/net.neoforged/bus/8.0.5/5b2d33285ab5d1554e9798ad98c40d6ea3868bd5/bus-8.0.5.jar:$JSPECIFY_JAR:$FASTUTIL_JAR:/root/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.2/minecraft-merged-deobf-26.2.jar" -d "$BUILD/neoforge-classes"
find "$ROOT/src/test/java" -name '*.java' -print0 | xargs -0 javac --release 17 -cp "$BUILD/classes" -d "$BUILD/test-classes"
java -ea -cp "$BUILD/classes:$BUILD/test-classes" org.neofabric.core.NeoFabricRuntimeTest
java -ea -cp "$BUILD/classes:$BUILD/test-classes" org.neofabric.core.CompatibilityClassLoaderTest
java -ea -cp "$BUILD/classes:$BUILD/test-classes" org.neofabric.core.CompatibilityEventBridgeTest
java -ea -cp "$BUILD/classes:$BUILD/test-classes" org.neofabric.core.ForeignFmlEventBridgeTest
java -ea -cp "$BUILD/classes:$BUILD/test-classes" org.neofabric.core.FabricEntrypointBridgeTest
jar --create --file "$BUILD/libs/neofabric-core-0.2.0-dev.jar" -C "$BUILD/classes" .
if [[ ! -f "$ROOT/forge-adapter/build/libs/examplemod-0.3.0-dev.jar" ]]; then
    echo "Forge adapter build missing; building Forge host module from source"
    (cd "$ROOT/forge-adapter" && ./gradlew --no-daemon build -x test)
fi
if [[ ! -f "$ROOT/forge-adapter/build/libs/examplemod-0.3.0-dev.jar" ]]; then
    echo "Forge host module build failed" >&2
    exit 1
fi
rm -rf "$BUILD/forge-classes" && mkdir -p "$BUILD/forge-classes"
(cd "$BUILD/forge-classes" && jar xf "$ROOT/forge-adapter/build/libs/examplemod-0.3.0-dev.jar" org/neofabric/forge/NeoFabricForgeAdapter.class)
mkdir -p "$BUILD/fml-patch"
FML_PATCH_CP="$ROOT/vendor/fml-loader-11.0.16.jar:$ROOT/vendor/neoforge-26.2.0.86.jar:$BUILD/classes:/home/nightwatchmaker/.local/share/PrismLauncher/libraries/com/mojang/logging/1.7.12/logging-1.7.12.jar:/home/nightwatchmaker/.local/share/PrismLauncher/libraries/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar:/games/PrismLauncher/instances/26.2(1)/libraries/maven-artifact-3.8.5.jar:/root/.gradle/caches/modules-2/files-2.1/org.jetbrains/annotations/24.0.1/13c5c75c4206580aa4d683bffee658caae6c9f43/annotations-24.0.1.jar"
javac --release 25 -cp "$FML_PATCH_CP" \
    -d "$BUILD/fml-patch" \
    "$ROOT/vendor/fancymodloader-src/loader/src/main/java/net/neoforged/fml/loading/moddiscovery/ModDiscoverer.java"
cp -f "$ROOT/vendor/fancymodloader-src/loader/build/libs/loader-11.0.16-neofabric.1.jar" "$BUILD/libs/neofabric-loader-3.9.0-dev.jar"
jar uf "$BUILD/libs/neofabric-loader-3.9.0-dev.jar" \
    -C "$BUILD/fml-patch" net/neoforged/fml/loading/moddiscovery/ModDiscoverer.class \
    -C "$BUILD/classes" . \
    -C "$ROOT/src/main/resources" META-INF/services/net.neoforged.neoforgespi.transformation.ClassProcessorProvider \
    -C "$BUILD/adapter-classes" . \
    -C "$BUILD/neoforge-classes" . \
    -C "$BUILD/forge-classes" . \
    -C "$ROOT/src/adapter/resources" fabric.mod.json \
    -C "$ROOT/src/adapter/resources" META-INF/neoforge.mods.toml \
    -C "$ROOT/forge-adapter/src/main/resources" META-INF/mods.toml
mkdir -p "$BUILD/fabric-runtime"
(cd "$BUILD/fabric-runtime" && jar xf "$ROOT/vendor/fabric-loader-0.19.5.jar")
jar uf "$BUILD/libs/neofabric-loader-3.9.0-dev.jar" \
    -C "$BUILD/fabric-runtime" net/fabricmc
mkdir -p "$BUILD/neoforge-runtime"
(cd "$BUILD/neoforge-runtime" && jar xf "$ROOT/vendor/neoforge-26.2.0.86.jar")
jar uf "$BUILD/libs/neofabric-loader-3.9.0-dev.jar" \
    -C "$BUILD/neoforge-runtime" .
mkdir -p "$BUILD/asm-runtime"
(cd "$BUILD/asm-runtime" && jar xf "$ASM_JAR")
mkdir -p "$BUILD/asm-tree-runtime"
(cd "$BUILD/asm-tree-runtime" && jar xf "$ASM_TREE_JAR")
jar uf "$BUILD/libs/neofabric-loader-3.9.0-dev.jar" \
    -C "$BUILD/asm-runtime" org/objectweb/asm \
    -C "$BUILD/asm-tree-runtime" org/objectweb/asm/tree
# Apply the compatibility scanner patch last: bundled NeoForge/FML classes
# above may contain the same class and otherwise overwrite this fix.
jar uf "$BUILD/libs/neofabric-loader-3.9.0-dev.jar" \
    -C "$BUILD/fml-patch" net/neoforged/fml/loading/moddiscovery/ModDiscoverer.class \
    -C "$BUILD/fml-patch" 'net/neoforged/fml/loading/moddiscovery/ModDiscoverer$DiscoveryPipeline.class'
# Replace native FML's user-facing product name in English error text.
mkdir -p "$BUILD/fml-lang/lang"
unzip -p "$ROOT/vendor/fml-loader-11.0.16.jar" lang/en_us.json \
    | sed 's/Your NeoForge installation is corrupted/Your NeoFabric installation is corrupted/g; s/reinstall NeoForge/reinstall NeoFabric/g' \
    > "$BUILD/fml-lang/lang/en_us.json"
zip -q -d "$BUILD/libs/neofabric-loader-3.9.0-dev.jar" lang/en_us.json || true
jar uf "$BUILD/libs/neofabric-loader-3.9.0-dev.jar" \
    -C "$BUILD/fml-lang" lang/en_us.json
chmod 777 "$BUILD/libs/neofabric-loader-3.9.0-dev.jar"
mkdir -p "$BUILD/mods"
# Host-native mod artifacts: the host loader supplies its own API/runtime.
jar --create --file "$BUILD/mods/NeoFabric-Fabric-0.3.0-dev.jar" \
    -C "$BUILD/classes" org/neofabric/core \
    -C "$BUILD/adapter-classes" org/neofabric/fabric \
    -C "$BUILD/adapter-classes" net/minecraftforge \
    -C "$ROOT/src/adapter/resources" fabric.mod.json
jar --create --file "$BUILD/mods/NeoFabric-Forge-0.3.0-dev.jar" \
    -C "$BUILD/classes" org/neofabric/core \
    -C "$BUILD/forge-classes" org/neofabric/forge \
    -C "$ROOT/forge-adapter/src/main/resources" META-INF/mods.toml \
    -C "$ROOT/forge-adapter/src/main/resources" pack.mcmeta
jar --create --file "$BUILD/mods/NeoFabric-NeoForge-0.3.0-dev.jar" \
    -C "$BUILD/classes" org/neofabric/core \
    -C "$BUILD/neoforge-classes" org/neofabric/neoforge \
    -C "$ROOT/src/adapter/resources" META-INF/neoforge.mods.toml \
    -C "$ROOT/forge-adapter/src/main/resources" pack.mcmeta
chmod 777 "$BUILD/mods"/*.jar
printf 'Built host mods:\n'
printf '  %s\n' "$BUILD/mods"/*.jar
