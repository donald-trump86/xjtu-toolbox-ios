#!/usr/bin/env python3
"""Upload a local file to the release OSS bucket."""
import os
import sys

import oss2


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: oss_upload.py <local-file> <object-key>", file=sys.stderr)
        return 2

    src, key = sys.argv[1], sys.argv[2]
    endpoint = os.environ["OSS_ENDPOINT"]
    if not endpoint.startswith(("http://", "https://")):
        endpoint = "https://" + endpoint

    bucket = oss2.Bucket(
        oss2.Auth(os.environ["OSS_ACCESS_KEY_ID"], os.environ["OSS_ACCESS_KEY_SECRET"]),
        endpoint,
        os.environ["OSS_BUCKET"],
    )
    bucket.put_object_from_file(key, src)
    print(f"uploaded {src} -> oss://{os.environ['OSS_BUCKET']}/{key}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
