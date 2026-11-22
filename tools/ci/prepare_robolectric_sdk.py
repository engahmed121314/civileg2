#!/usr/bin/env python3
"""Prefetch the unchanged Robolectric SDK once, verify it, and fail without retries."""
import argparse
import hashlib
from pathlib import Path
import sys
import tempfile
import urllib.error
import urllib.request

ARTIFACT = "android-all-instrumented-14-robolectric-10818077-i7.jar"
# Maven Central publisher checksum, independently read before selecting the transport.
CHECKSUM_URL = (
    "https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented/"
    "14-robolectric-10818077-i7/" + ARTIFACT + ".sha512"
)
SHA512 = (
    "7cdc0b1306e7865f1acd25a363073149654caee5b8e278c5ae37ff4724185724"
    "eff39f0b0111800b521e640bb28698d9d2e655134a801569a0f07470e7b5f455"
)
# Google-hosted Central mirror is transport only, not a separate trusted checksum source.
DOWNLOAD_URL = (
    "https://maven-central.storage-download.googleapis.com/maven2/org/robolectric/"
    "android-all-instrumented/14-robolectric-10818077-i7/" + ARTIFACT
)


def sha512(path):
    digest = hashlib.sha512()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def prepare_sdk(directory, opener=urllib.request.urlopen):
    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    destination = directory / ARTIFACT
    if destination.exists():
        if sha512(destination) != SHA512:
            raise RuntimeError("Cached Robolectric SDK checksum mismatch; refusing to run tests")
        print("Verified cached Robolectric SDK: " + ARTIFACT)
        return destination

    pending = None
    try:
        # Exactly one request. In particular, never retry or switch hosts on HTTP429.
        with opener(DOWNLOAD_URL, timeout=120) as response:
            with tempfile.NamedTemporaryFile(dir=directory, suffix=".partial", delete=False) as target:
                pending = Path(target.name)
                for chunk in iter(lambda: response.read(1024 * 1024), b""):
                    target.write(chunk)
        if sha512(pending) != SHA512:
            raise RuntimeError("Downloaded Robolectric SDK checksum mismatch; refusing to run tests")
        pending.replace(destination)
        print("Verified downloaded Robolectric SDK: " + ARTIFACT)
        return destination
    except urllib.error.HTTPError as error:
        retry_after = error.headers.get("Retry-After", "not supplied")
        raise RuntimeError(
            f"Robolectric SDK HTTP{error.code}; stopped without retry. Retry-After: {retry_after}"
        ) from error
    finally:
        if pending is not None:
            pending.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", required=True)
    args = parser.parse_args()
    try:
        prepare_sdk(args.directory)
    except (OSError, RuntimeError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
