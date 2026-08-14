import hashlib
import json
import subprocess
import sys
import time
from pathlib import Path

REPO_NAME = "vtorres-t/ext"
RETRY_ATTEMPTS = 4
RETRY_BASE_DELAY = 60  # Documented minimum wait; doubles per attempt.
UPLOAD_CHUNK_SIZE = 80
UPLOAD_CHUNK_INTERVAL = 30

def run_gh(*args: str, success_errors: tuple[str, ...] = ()) -> str:
    attempt = 1
    delay = RETRY_BASE_DELAY
    while True:
        result = subprocess.run(
            ["gh", *args],
            capture_output=True,
            encoding="utf-8",
            check=False,
        )
        if result.returncode == 0:
            return result.stdout.strip()

        error = result.stderr.lower()
        if any(success_error in error for success_error in success_errors):
            return result.stdout.strip()

        if "secondary rate limit" in error and attempt < RETRY_ATTEMPTS:
            retry_delay = delay
            delay *= 2
        elif "api rate limit exceeded" in error and attempt < RETRY_ATTEMPTS:
            rate_limit = subprocess.run(
                ["gh", "api", "rate_limit", "--jq", ".resources.core.reset"],
                capture_output=True,
                encoding="utf-8",
                check=False,
            )
            retry_delay = RETRY_BASE_DELAY
            if rate_limit.returncode == 0:
                retry_delay = max(
                    int(rate_limit.stdout.strip()) - int(time.time()) + 10,
                    RETRY_BASE_DELAY,
                )
        else:
            raise RuntimeError(f"gh {' '.join(args)} failed: {result.stderr.strip()}")

        print(
            f"GitHub rate limit hit; retrying in {retry_delay}s "
            f"(attempt {attempt}/{RETRY_ATTEMPTS})"
        )
        time.sleep(retry_delay)
        attempt += 1

def create_release(tag: str):
    if run_gh(
        "release",
        "view",
        tag,
        "--repo",
        REPO_NAME,
        "--json",
        "tagName",
        success_errors=("release not found",),
    ):
        print(f"Release {tag} already exists")
        return

    print(f"Creating release {tag}")
    run_gh(
        "release",
        "create",
        tag,
        "--repo",
        REPO_NAME,
        "--draft",
        "--title",
        f"Repository Update {tag}",
        "--notes",
        f"Automated update from vtorres-t/extensions-source",
    )


def publish_release(tag: str):
    print(f"Publishing release {tag}")
    run_gh("release", "edit", tag, "--repo", REPO_NAME, "--draft=false")

def get_release_assets(tag: str) -> dict[str, str]:
    release = json.loads(
        run_gh(
            "release",
            "view",
            tag,
            "--repo",
            REPO_NAME,
            "--json",
            "assets",
        )
    )
    return {
        asset["name"]: (asset.get("digest") or "").removeprefix("sha256:")
        for asset in release["assets"]
    }

def upload_assets(tag: str, files: list[Path]):
    if not files:
        return

    existing_assets = get_release_assets(tag)
    files_to_upload = [
        file
        for file in files
        if existing_assets.get(file.name)
        != hashlib.sha256(file.read_bytes()).hexdigest()
    ]
    skipped = len(files) - len(files_to_upload)
    print(f"Uploading {len(files_to_upload)} assets to {tag}, skipping {skipped}")

    for i in range(0, len(files_to_upload), UPLOAD_CHUNK_SIZE):
        chunk = files_to_upload[i : i + UPLOAD_CHUNK_SIZE]
        if i:
            time.sleep(UPLOAD_CHUNK_INTERVAL)
        print(f"  assets {i + 1}-{i + len(chunk)} of {len(files_to_upload)}")
        run_gh(
            "release",
            "upload",
            tag,
            *[str(f) for f in chunk],
            "--repo",
            REPO_NAME,
            "--clobber",
        )
    publish_release(tag)
