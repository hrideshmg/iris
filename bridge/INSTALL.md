# Iris Bridge — Install Guide

## 1. Clone the repo

```bash
cd /home/user
git clone <repo-url> iris
```

## 2. Verify config.py paths

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
```ini
IRIS_HERMES_HOME=/home/actualuser/.hermes
IRIS_HERMES_SRC=/home/actualuser/.hermes/hermes-agent
IRIS_HERMES_CLI=/home/actualuser/.hermes/venv/bin/hermes
```

> `IRIS_HERMES_CLI` must be the **full path** to the hermes binary — systemd runs with a minimal PATH and won't resolve bare command names.
> Find it with: `which hermes` (inside the Hermes venv).

## 3. Install dependencies

```bash
/home/user/.hermes/venv/bin/pip install -r /home/user/iris/bridge/requirements.txt
```

## 4. Provision a device token

```bash
cd /home/user/iris/bridge
/home/user/.hermes/venv/bin/python manage_tokens.py add phone-3a
# Copy the printed token — paste it into the Iris app on the phone.
```

## 5. Install the systemd service

Substitute your actual username before installing:

```bash
sed "s|/home/user|/home/$USER|g; s|User=user|User=$USER|g" \
  /home/$USER/iris/bridge/iris-bridge.service \
  | sudo tee /etc/systemd/system/iris-bridge.service > /dev/null

sudo systemctl daemon-reload
sudo systemctl enable --now iris-bridge
```

Verify it's running:
```bash
systemctl status iris-bridge --no-pager
curl http://localhost:8807/healthz   # expect: {"ok":true}
```

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
