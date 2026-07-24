# Iris Bridge — Install Guide


## 0. Prerequisites

- A working Hermes installation with a source tree containing `tools/transcription_tools.py`.
- `systemd --user` available (`systemctl --user is-active default.target`).
The commands below use `$HOME/iris`, `$HOME/.hermes`, and the common source-install venv at `$HOME/.hermes/hermes-agent/venv`. Discover and verify the paths first instead of assuming an older `$HOME/.hermes/venv` layout.

```bash
export IRIS_HOME="$HOME/iris"
export HERMES_HOME="${HERMES_HOME:-$HOME/.hermes}"
export HERMES_SRC="$HERMES_HOME/hermes-agent"
export HERMES_VENV="$HERMES_SRC/venv"
export HERMES_CLI="$HERMES_VENV/bin/hermes"

[ -f "$HERMES_SRC/tools/transcription_tools.py" ]
[ -x "$HERMES_VENV/bin/python" ]
[ -x "$HERMES_CLI" ]
"$HERMES_CLI" --version

## 1. Clone the repo

```bash
cd /home/user
git clone <repo-url> iris
```

## 2. Verify config.py and create .env

Open `bridge/config.py` and confirm these match your environment:

```python
hermes_home: Path = Path("/home/user/.hermes")        # Hermes working directory
hermes_src:  Path = Path("/home/user/.hermes/hermes-agent")  # must contain tools/transcription_tools.py
hermes_cli:  str  = "hermes"                           # must resolve in the venv below
```

To verify:
```bash
# Check transcription tools exist
ls /home/user/.hermes/hermes-agent/tools/transcription_tools.py

# Check hermes CLI is on PATH inside the venv
/home/user/.hermes/venv/bin/python -c "from tools.transcription_tools import transcribe_audio; print('ok')"
```

Create `bridge/.env` with your actual paths — this is required:
```bash
cat > "$IRIS_HOME/bridge/.env" <<EOF
IRIS_HERMES_HOME=$HERMES_HOME
IRIS_HERMES_SRC=$HERMES_SRC
IRIS_HERMES_CLI=$HERMES_CLI
IRIS_HOST=127.0.0.1
IRIS_PORT=8807
EOF
chmod 600 "$IRIS_HOME/bridge/.env"
```

> `IRIS_HERMES_CLI` must be the **full path** to the hermes binary — systemd runs with a minimal PATH and won't resolve bare command names.
> Find it with: `which hermes` (inside the Hermes venv).

## 3. Install dependencies

```bash
/home/user/.hermes/venv/bin/pip install -r /home/user/iris/bridge/requirements.txt
```

## 4. Provision a device token


Create one token per phone/device. The token is the only bridge secret stored on the phone; it must never be committed to Git, or shared with another device.

```bash
cd "$IRIS_HOME/bridge"
"$HERMES_VENV/bin/python" manage_tokens.py add phone-3a
"$HERMES_VENV/bin/python" manage_tokens.py list
stat -c '%a %n' auth.json  # expect: 600 auth.json
```

Copy the token directly into the Iris app. `auth.json` is the local token store and is expected to have mode `0600`.

## 5. Install the bridge as a user service (no sudo)

Create `~/.config/systemd/user/iris-bridge.service`:

```bash
mkdir -p "$HOME/.config/systemd/user"
cat > "$HOME/.config/systemd/user/iris-bridge.service" <<EOF
[Unit]
Description=Iris PTT Bridge
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=$IRIS_HOME/bridge
Environment=HERMES_HOME=$HERMES_HOME
Environment=PYTHONPATH=$HERMES_SRC
# Required: the bridge imports Hermes STT directly, so it needs Hermes' provider keys.
EnvironmentFile=$HERMES_HOME/.env
EnvironmentFile=$IRIS_HOME/bridge/.env
ExecStart=$HERMES_VENV/bin/uvicorn main:app --host 127.0.0.1 --port 8807
Restart=on-failure
RestartSec=3
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ReadWritePaths=$IRIS_HOME/bridge
UMask=0077

[Install]
WantedBy=default.target
EOF

systemd-analyze --user verify "$HOME/.config/systemd/user/iris-bridge.service"
systemctl --user daemon-reload
systemctl --user enable --now iris-bridge
```

Verify the intended service, listener, and access-control boundary:

```bash
systemctl --user is-enabled iris-bridge  # expect: enabled
systemctl --user is-active iris-bridge   # expect: active
curl --fail --silent --show-error http://127.0.0.1:8807/healthz
# expect: {"ok":true}

curl --silent -o /dev/null -w '%{http_code}\n' \
  -F 'file=@/etc/hosts;filename=test.m4a' \
  http://127.0.0.1:8807/ptt/audio
# expect: 401

ss -ltn '( sport = :8807 )'
# expect a listener only on 127.0.0.1:8807
```

For a user service to remain available after logout and across reboot, check lingering:

```bash
loginctl show-user "$USER" -p Linger
# expect: Linger=yes
```

If it is `no`, enabling it requires a one-time local administrator action: `sudo loginctl enable-linger "$USER"`.

## 6. Expose the bridge publicly

The bridge is running on `localhost:8807`. You need to make `/ptt/audio` and `/healthz` reachable from the phone.

**Detect what's available:**

```bash
which cloudflared  && echo "CF tunnel available"
which caddy        && echo "Caddy available"
which nginx        && echo "nginx available"
which certbot      && echo "certbot available"
```

Then follow the section that matches your setup, prompt the user for confirmation before making changes.

---

### Option A — Cloudflare Tunnel (recommended, no open ports)

```bash
cloudflared tunnel create iris
cloudflared tunnel route dns iris <your-subdomain>.yourdomain.com
```

Append `~/.cloudflared/config.yml`:
```yaml
tunnel: iris
credentials-file: /home/user/.cloudflared/<tunnel-id>.json
ingress:
  - hostname: <your-subdomain>.yourdomain.com
    service: http://localhost:8807
  - service: http_status:404
```

```bash
cloudflared tunnel run iris
# To run as a service:
sudo cloudflared service install
sudo systemctl enable --now cloudflared
```

No firewall changes needed — traffic never hits an open port.

---

### Option B — Cloudflare + existing reverse proxy (Caddy or nginx)

The bridge binds to `127.0.0.1:8807` by default — change the bind in `.env` if needed:
```ini
IRIS_HOST=127.0.0.1
IRIS_PORT=8807
```

**Caddy** — add to your Caddyfile:
```
<your-subdomain>.yourdomain.com {
    reverse_proxy localhost:8807
}
```
```bash
sudo systemctl reload caddy
```

**nginx** — add a server block:
```nginx
server {
    listen 443 ssl;
    server_name <your-subdomain>.yourdomain.com;
    # TLS managed by Cloudflare origin cert or certbot

    location / {
        proxy_pass http://127.0.0.1:8807;
    }
}
```
```bash
sudo nginx -t && sudo systemctl reload nginx
```

Then in Cloudflare dashboard: add a DNS A record pointing `<your-subdomain>` to your server IP, proxy enabled (orange cloud).

**Firewall** — only allow Cloudflare IPs on the exposed port:
```bash
sudo ufw deny 8807
# Add Cloudflare ranges from https://www.cloudflare.com/ips/
```

---

### Verify

```bash
curl https://<your-subdomain>.yourdomain.com/healthz
# expect: {"ok":true}
```
