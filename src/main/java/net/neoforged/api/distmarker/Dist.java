package net.neoforged.api.distmarker;

/**
 * Minimal runtime distribution marker required by the embedded FML bootstrap.
 * NeoFabric packages this small API type because the standalone FML loader
 * artifact does not include the separate NeoForge API dependency jar.
 */
public enum Dist {
    CLIENT,
    DEDICATED_SERVER
}
