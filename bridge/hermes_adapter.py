"""Thin adapter over Hermes: transcription (direct import) + agent turn (CLI)."""
import subprocess
import sys
from pathlib import Path

from config import settings

_PTT_SESSION_ID = "phone-ptt"


def _ensure_hermes_importable() -> None:
    src = str(settings.hermes_src)
    if src not in sys.path:
        sys.path.insert(0, src)


def transcribe(file_path: Path) -> str:
    _ensure_hermes_importable()
    try:
        from tools.transcription_tools import transcribe_audio  # type: ignore
    except Exception as e:
        raise RuntimeError(f"cannot import Hermes transcription tools: {e}") from e

    result = transcribe_audio(str(file_path))
    if not result.get("success"):
        raise RuntimeError(f"transcription failed: {result.get('error', 'unknown')}")
    text = result.get("transcript", "").strip()
    if not text:
        raise RuntimeError("transcription returned empty text")
    return text


def run_agent_turn(transcript: str) -> tuple[str, str]:
    cmd = [
        settings.hermes_cli, "chat",
        "-Q",
        "-q", transcript,
        "--resume", _PTT_SESSION_ID,
    ]
    try:
        proc = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=settings.agent_timeout_s,
            cwd=str(settings.hermes_home),
        )
    except subprocess.TimeoutExpired as e:
        raise RuntimeError("agent turn timed out") from e

    if proc.returncode != 0:
        raise RuntimeError(f"hermes chat failed ({proc.returncode}): {proc.stderr.strip()[:500]}")

    response = proc.stdout.strip()
    if not response:
        raise RuntimeError("agent returned empty response")
    return response, _PTT_SESSION_ID
