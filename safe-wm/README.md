# SAFE-WM

Standalone SAFE-WM MVP workspace.

This directory is intentionally structured so it can be split into a separate repository later. QAIMA code may be referenced for API contracts and behavior, but SAFE-WM should integrate with QAIMA through HTTP adapters only.

## Run

Backend:

```bash
cd safe-wm/backend
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port 8100
```

Frontend:

```bash
cd safe-wm/frontend
npm install
npm run dev
```

Open `http://127.0.0.1:5174`.

## MVP APIs

```text
GET  /api/wm/health
GET  /api/wm/customers
GET  /api/wm/customers/{customer_id}
POST /api/wm/customers/{customer_id}/analysis
GET  /api/wm/dashboard/risk-summary
GET  /api/wm/dashboard/high-risk-customers
GET  /api/wm/dashboard/customer-alerts
```

## Verify

```bash
cd safe-wm/backend
.venv/bin/python -m pytest -q

cd ../frontend
npm run build
```
