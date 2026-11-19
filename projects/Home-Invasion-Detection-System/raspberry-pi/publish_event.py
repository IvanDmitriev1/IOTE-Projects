"""Publish one completed PIR capture to the private Home Watch Firebase contract."""

from pathlib import Path
import base64
import hashlib
import re

from google.api_core.exceptions import PreconditionFailed


MAX_JPEG_BYTES = 5 * 1024 * 1024


def publish_event(database, bucket, home_id: str, event_id: str, detected_at_ms: int, image_path: str) -> None:
    """Upload the JPEG first, then create its RTDB event using the caller's stable ID."""
    if not re.fullmatch(r"[A-Za-z0-9_-]+", home_id) or not re.fullmatch(r"[A-Za-z0-9_-]+", event_id):
        raise ValueError("event_id must be a Firebase-key-safe stable identifier")
    if detected_at_ms <= 0:
        raise ValueError("detected_at_ms must be UTC epoch milliseconds")
    image = Path(image_path)
    if image.suffix.lower() not in {".jpg", ".jpeg"}:
        raise ValueError("the snapshot must be a JPEG")
    size = image.stat().st_size
    if size > MAX_JPEG_BYTES:
        raise ValueError("the JPEG must be no larger than 5 MiB")
    if size < 4:
        raise ValueError("the snapshot is not a valid JPEG")
    with image.open("rb") as stream:
        if stream.read(2) != b"\xff\xd8":
            raise ValueError("the snapshot is not a valid JPEG")
        stream.seek(-2, 2)
        if stream.read(2) != b"\xff\xd9":
            raise ValueError("the snapshot is not a valid JPEG")

    digest = base64.b64encode(hashlib.md5(image.read_bytes()).digest()).decode("ascii")

    snapshot_path = f"homes/{home_id}/events/{event_id}.jpg"
    snapshot = bucket.blob(snapshot_path)
    try:
        snapshot.upload_from_filename(str(image), content_type="image/jpeg", if_generation_match=0)
    except PreconditionFailed as error:
        snapshot.reload()
        if snapshot.md5_hash != digest:
            raise ValueError("a different snapshot already exists for this event ID") from error
    record = {
        "detectedAt": int(detected_at_ms),
        "snapshotPath": snapshot_path,
    }
    database.reference(f"homes/{home_id}/events/{event_id}").transaction(
        lambda current: current if current is not None else record
    )
