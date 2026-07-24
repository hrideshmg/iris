import subprocess
import sys
import uuid
from pathlib import Path

from config import settings

_ONE_WAY_DIRECTIVE = (
    "[Iris one-way voice note — the user CANNOT see follow-up questions or "
    "confirm anything. Never ask for confirmation and never wait for a reply. "
    "Execute reversible actions immediately (create calendar events, take "
    "notes, set reminders, look things up). For IRREVERSIBLE or destructive "
    "actions (deleting, sending email, sharing, overwriting, payments), do NOT "
    "perform them — instead record the request as a note/task for later human "
    "review and say so. Assume sensible defaults (e.g. 1-hour event duration, "
    "the user's local timezone) rather than asking. Keep the reply short.]\n\n"
)


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


def _session_db():
    _ensure_hermes_importable()
    try:
        from hermes_state import SessionDB  # type: ignore
    except Exception as e:
        raise RuntimeError(f"cannot import Hermes session store: {e}") from e
    return SessionDB()


def run_agent_turn(transcript: str) -> str:
    db = _session_db()
    session_id = f"iris-ptt-{uuid.uuid4().hex}"
    db.create_session(session_id, source="iris-bridge", cwd=str(settings.hermes_home))
    try:
        cmd = [
            settings.hermes_cli, "chat",
            "-Q",
            "--no-restore-cwd",
            "-q", _ONE_WAY_DIRECTIVE + transcript,
            "--resume", session_id,
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

        response = proc.stdout
        if not response:
            raise RuntimeError("agent returned empty response")
        return response
    finally:
        try:
            db.delete_session(session_id)
        except Exception:
            pass