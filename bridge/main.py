import tempfile
from pathlib import Path

from fastapi import Depends, FastAPI, HTTPException, UploadFile, status
from pydantic import BaseModel

from auth import require_device
from hermes_adapter import run_agent_turn, transcribe

app = FastAPI(title="Iris Bridge")


class PttResponse(BaseModel):
    transcript: str
    response: str
    session_id: str


@app.get("/healthz")
def healthz() -> dict:
    return {"ok": True}


@app.post("/ptt/audio", response_model=PttResponse)
async def ptt_audio(
    file: UploadFile,
    device: str = Depends(require_device),
) -> PttResponse:
    suffix = Path(file.filename or "audio.m4a").suffix or ".m4a"
    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
    try:
        tmp.write(await file.read())
        tmp.flush()
        tmp.close()
        try:
            transcript = transcribe(Path(tmp.name))
            response, session_id = run_agent_turn(transcript)
        except RuntimeError as e:
            raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(e))
        return PttResponse(transcript=transcript, response=response, session_id=session_id)
    finally:
        Path(tmp.name).unlink(missing_ok=True)
