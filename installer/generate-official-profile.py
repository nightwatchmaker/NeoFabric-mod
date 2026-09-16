#!/usr/bin/env python3
"""Generate an official-launcher child profile from a real Mojang version JSON."""
import argparse
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-json", type=Path, required=True)
    parser.add_argument("--loader-jar", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--id", default="NeoFabric-26.2-dev")
    args = parser.parse_args()

    if not args.base_json.is_file():
        raise SystemExit(f"Base version JSON not found: {args.base_json}")
    if not args.loader_jar.is_file():
        raise SystemExit(f"Loader JAR not found: {args.loader_jar}")

    base = json.loads(args.base_json.read_text())
    for field in ("id", "mainClass", "libraries"):
        if field not in base:
            raise SystemExit(f"Base version JSON is missing required field: {field}")

    # This is deliberately a child manifest, not a fake replacement Minecraft JAR.
    # The loader bootstrap must eventually be a real launcher-compatible library.
    profile = {
        "id": args.id,
        "inheritsFrom": base["id"],
        "type": "custom",
        "mainClass": "org.neofabric.launcher.NeoFabricLauncher",
        "arguments": base.get("arguments", {}),
        "jvmArguments": base.get("jvmArguments", []),
        "libraries": base["libraries"] + [{
        "name": "org.neofabric:loader:3.9.0-dev"
    }],
        "neoFabric": {
            "development": True,
            "loaderJar": args.loader_jar.name,
            "targetMainClass": base["mainClass"],
            "status": "bootstrap-integration-required"
        }
    }
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / f"{args.id}.json").write_text(json.dumps(profile, indent=2) + "\n")
    (args.output_dir / f"{args.id}.jar").write_bytes(args.loader_jar.read_bytes())
    print(f"Generated development profile: {args.output_dir / (args.id + '.json')}")
    print("Warning: official launcher bootstrap integration is still required before gameplay.")


if __name__ == "__main__":
    main()
