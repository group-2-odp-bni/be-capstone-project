import os
from flask import jsonify
from flask_swagger_ui import get_swaggerui_blueprint

from app import app  

try:
    from swagger_registry import SWAGGER_REGISTRY
except ImportError:
    SWAGGER_REGISTRY = {}


def _normalize_path(rule):
    """
    Ubah /api/v1/split-bill/bills/<bill_id> -> /api/v1/split-bill/bills/{bill_id}
    supaya valid di OpenAPI.
    """
    return str(rule).replace("<", "{").replace(">", "}")


def generate_openapi_from_flask(flask_app):
    paths = {}

    for rule in flask_app.url_map.iter_rules():
        if rule.endpoint == "static":
            continue

        methods = [m for m in rule.methods if m in ["GET", "POST", "PUT", "PATCH", "DELETE"]]
        if not methods:
            continue

        path = _normalize_path(rule)

        if path not in paths:
            paths[path] = {}

        for method in methods:
            method_lower = method.lower()

            default_spec = {
                "summary": rule.endpoint,
                "responses": {
                    "200": {"description": "Success"}
                }
            }

            custom = (
                SWAGGER_REGISTRY.get(path, {}).get(method_lower, {})
            )

            merged = {**default_spec, **custom}

            if "responses" in custom:
                merged["responses"] = custom["responses"]

            paths[path][method_lower] = merged

    openapi_spec = {
        "openapi": "3.0.0",
        "info": {
            "title": "Split Bill API",
            "version": "1.0.0",
            "description": "Dokumentasi otomatis untuk layanan Split Bill (Orange).",
        },
        "paths": paths,
    }
    return openapi_spec


@app.route("/openapi.json")
def openapi_json():
    spec = generate_openapi_from_flask(app)
    return jsonify(spec)

SWAGGER_URL = "/docs"
API_URL = "/openapi.json"

swaggerui_blueprint = get_swaggerui_blueprint(
    SWAGGER_URL,
    API_URL,
    config={"app_name": "Split Bill API"},
)

app.register_blueprint(swaggerui_blueprint, url_prefix=SWAGGER_URL)


if __name__ == "__main__":
    port = int(os.getenv("PORT", 5000))
    app.run(host="0.0.0.0", port=port, debug=True)
