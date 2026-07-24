# Iris

A Nothing Phone 3a companion app for [hermes-agent](https://github.com/nousresearch/hermes-agent). Hold the Essential Key and speak to Hermes directly.

## Deploy the bridge

Paste this into Hermes:

```
Read https://raw.githubusercontent.com/hrideshmg/iris/refs/heads/main/bridge/INSTALL.md and follow every step to deploy the Iris PTT bridge.
```

The bridge is the backend that the Android app connects to. It communicates with Hermes and performs speech transcription using the same STT model configured for Hermes.

Note that the bridge must be reachable from the internet so the Android app can connect to it. I'd recommend Cloudflare tunnels for this purpose, the INSTALL.md walks you through it. 

## Structure

- `bridge/` — FastAPI backend that runs on your VPS/Homelab
- `app/` — Android app for the Nothing Phone 3a, uses the Android accessibility service to detect the essential key.
