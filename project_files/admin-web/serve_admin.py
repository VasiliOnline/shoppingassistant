from __future__ import annotations

import argparse
import json
import mimetypes
import posixpath
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin, urlparse
from urllib.request import Request, urlopen


ROOT_DIR = Path(__file__).resolve().parent
CONFIG_PATH = ROOT_DIR / ".admin-web-config.json"
DEFAULT_BACKEND = "http://127.0.0.1:8081"


def normalize_backend_url(url: str) -> str:
    normalized = url.strip().rstrip("/")
    parsed = urlparse(normalized)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise ValueError("Backend URL must include http/https and host")
    return normalized


def load_config(default_backend: str) -> dict[str, Any]:
    if CONFIG_PATH.exists():
        try:
            stored = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
            backend_url = normalize_backend_url(stored.get("backendUrl", default_backend))
            return {"backendUrl": backend_url}
        except Exception:
            pass
    return {"backendUrl": normalize_backend_url(default_backend)}


def save_config(config: dict[str, Any]) -> None:
    CONFIG_PATH.write_text(
        json.dumps(config, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


class AdminProxyHandler(BaseHTTPRequestHandler):
    server_version = "CatalogGovernanceAdmin/1.0"

    @property
    def backend_url(self) -> str:
        return self.server.runtime_config["backendUrl"]  # type: ignore[attr-defined]

    def do_OPTIONS(self) -> None:
        if self.path.startswith("/__admin/api/") or self.path == "/__admin/config":
            self.send_response(204)
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Headers", "Content-Type")
            self.send_header("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS")
            self.end_headers()
            return
        self.send_error(404)

    def do_GET(self) -> None:
        if self.path == "/__admin/config":
            self._respond_json(200, self.server.runtime_config)  # type: ignore[attr-defined]
            return
        if self.path.startswith("/__admin/api/"):
            self._proxy_request()
            return
        self._serve_static()

    def do_POST(self) -> None:
        if self.path == "/__admin/config":
            self._update_config()
            return
        if self.path.startswith("/__admin/api/"):
            self._proxy_request()
            return
        self.send_error(404)

    def do_PUT(self) -> None:
        self.do_POST()

    def do_PATCH(self) -> None:
        self.do_POST()

    def do_DELETE(self) -> None:
        if self.path.startswith("/__admin/api/"):
            self._proxy_request()
            return
        self.send_error(404)

    def log_message(self, fmt: str, *args: Any) -> None:
        sys.stdout.write(
            "%s - - [%s] %s\n"
            % (self.address_string(), self.log_date_time_string(), fmt % args)
        )

    def _serve_static(self) -> None:
        request_path = self.path.split("?", 1)[0]
        safe_path = posixpath.normpath(request_path).lstrip("/")
        candidate = (ROOT_DIR / safe_path).resolve()
        if not str(candidate).startswith(str(ROOT_DIR)):
            self.send_error(403)
            return

        if request_path in {"", "/"}:
            candidate = ROOT_DIR / "index.html"
        elif not candidate.exists() or candidate.is_dir():
            candidate = ROOT_DIR / "index.html"

        if not candidate.exists():
            self.send_error(404)
            return

        mime_type, _ = mimetypes.guess_type(str(candidate))
        payload = candidate.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", mime_type or "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def _update_config(self) -> None:
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length > 0 else b"{}"
        try:
            body = json.loads(raw.decode("utf-8") or "{}")
            backend_url = normalize_backend_url(body.get("backendUrl", ""))
        except Exception as exc:
            self._respond_json(400, {"error": f"Invalid config payload: {exc}"})
            return

        self.server.runtime_config["backendUrl"] = backend_url  # type: ignore[attr-defined]
        save_config(self.server.runtime_config)  # type: ignore[attr-defined]
        self._respond_json(200, self.server.runtime_config)  # type: ignore[attr-defined]

    def _proxy_request(self) -> None:
        upstream_path = self.path.removeprefix("/__admin/api")
        upstream_url = urljoin(f"{self.backend_url}/", upstream_path.lstrip("/"))
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length) if length > 0 else None

        headers = {}
        for name in ("Content-Type", "Authorization", "Accept"):
            value = self.headers.get(name)
            if value:
                headers[name] = value

        request = Request(
            upstream_url,
            data=body,
            headers=headers,
            method=self.command,
        )
        try:
            with urlopen(request, timeout=120) as response:
                payload = response.read()
                content_type = response.headers.get("Content-Type", "application/json; charset=utf-8")
                self.send_response(response.status)
                self.send_header("Content-Type", content_type)
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
        except HTTPError as error:
            payload = error.read()
            content_type = error.headers.get("Content-Type", "application/json; charset=utf-8")
            self.send_response(error.code)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        except URLError as error:
            self._respond_json(
                502,
                {
                    "error": "Upstream unreachable",
                    "backendUrl": self.backend_url,
                    "details": str(error.reason),
                },
            )

    def _respond_json(self, status: int, payload: dict[str, Any]) -> None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def main() -> None:
    parser = argparse.ArgumentParser(description="Serve Catalog Governance Admin Web")
    parser.add_argument("--backend", default=DEFAULT_BACKEND, help="Backend base URL")
    parser.add_argument("--host", default="127.0.0.1", help="Host to bind")
    parser.add_argument("--port", type=int, default=4173, help="Port to bind")
    args = parser.parse_args()

    runtime_config = load_config(args.backend)
    runtime_config["backendUrl"] = normalize_backend_url(args.backend or runtime_config["backendUrl"])
    save_config(runtime_config)

    server = ThreadingHTTPServer((args.host, args.port), AdminProxyHandler)
    server.runtime_config = runtime_config  # type: ignore[attr-defined]
    print(f"Catalog Governance Admin available at http://{args.host}:{args.port}")
    print(f"Proxy target: {runtime_config['backendUrl']}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
