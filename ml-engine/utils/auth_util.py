import os, time, requests
from functools import wraps
from typing import Optional, Dict, Any
from jose import jwt, jwk
from jose.utils import base64url_decode
from jose.exceptions import JWTError, ExpiredSignatureError, JWTClaimsError
from flask import request, jsonify, g
from dotenv import load_dotenv

load_dotenv()

JWK_SET_URI = os.getenv("JWK_SET_URI")
JWT_ISSUER  = os.getenv("JWT_ISSUER")
JWT_AUDIENCE = os.getenv("JWT_AUDIENCE")             
JWKS_TTL = int(os.getenv("JWKS_CACHE_TTL_SEC", "300"))

_JWKS_CACHE: Dict[str, Any] = {"fetched_at": 0, "jwks": None}

def _get_jwks() -> Dict[str, Any]:
    now = time.time()
    if _JWKS_CACHE["jwks"] and (now - _JWKS_CACHE["fetched_at"]) < JWKS_TTL:
        return _JWKS_CACHE["jwks"]
    resp = requests.get(JWK_SET_URI, timeout=5)
    resp.raise_for_status()
    jwks = resp.json()
    _JWKS_CACHE["jwks"] = jwks
    _JWKS_CACHE["fetched_at"] = now
    return jwks

def _select_jwk_for_token(token: str) -> Dict[str, Any]:
    header = jwt.get_unverified_header(token)
    kid = header.get("kid")
    alg = header.get("alg", "RS256")
    jwks = _get_jwks()
    keys = jwks.get("keys", [])
    if not keys:
        raise JWTError("JWKS keys kosong. Pastikan /oauth2/jwks mengembalikan public keys.")
    if kid:
        for k in keys:
            if k.get("kid") == kid:
                k["_alg"] = alg
                return k
        _JWKS_CACHE["jwks"] = None
        jwks = _get_jwks()
        for k in jwks.get("keys", []):
            if k.get("kid") == kid:
                k["_alg"] = alg
                return k
        raise JWTError(f"Public key dengan kid={kid} tidak ditemukan.")
    keys[0]["_alg"] = alg
    return keys[0]

def decode_verify_jwt(token: str, issuer: Optional[str] = JWT_ISSUER, audience: Optional[str] = JWT_AUDIENCE) -> Dict[str, Any]:
    jwk_data = _select_jwk_for_token(token)
    public_key = jwk.construct(jwk_data, algorithm=jwk_data["_alg"])
    try:
        signing_input, encoded_sig = token.rsplit(".", 1)
    except ValueError:
        raise JWTError("Format JWT tidak valid.")
    decoded_sig = base64url_decode(encoded_sig.encode("utf-8"))
    if not public_key.verify(signing_input.encode("utf-8"), decoded_sig):
        raise JWTError("Signature verification failed")

    claims = jwt.get_unverified_claims(token)
    now = int(time.time())
    exp = int(claims.get("exp", 0))
    if now >= exp:
        raise ExpiredSignatureError("Token expired")

    if issuer is not None and claims.get("iss") != issuer:
        raise JWTClaimsError(f"Issuer mismatch. expected={issuer!r} actual={claims.get('iss')!r}")

    if audience is not None:
        aud = claims.get("aud")
        if isinstance(aud, str):
            if aud != audience:
                raise JWTClaimsError(f"Audience mismatch. expected={audience!r} actual={aud!r}")
        elif isinstance(aud, (list, tuple, set)):
            if audience not in aud:
                raise JWTClaimsError(f"Audience {audience!r} not in token aud {aud!r}")
        elif aud is not None:
            raise JWTClaimsError("Invalid 'aud' claim type")

    return claims

def require_auth(func):
    @wraps(func)
    def wrapper(*args, **kwargs):
        auth = request.headers.get("Authorization", "")
        if not auth.startswith("Bearer "):
            return jsonify({"error": "missing_bearer_token"}), 401
        token = auth.split(" ", 1)[1].strip()
        try:
            claims = decode_verify_jwt(token)
            g.jwt_claims = claims
        except (ExpiredSignatureError, JWTClaimsError, JWTError) as e:
            return jsonify({"error": "invalid_token", "detail": str(e)}), 401
        return func(*args, **kwargs)
    return wrapper

def _scopes_from_claims(claims: Dict[str, Any]) -> set:
    scopes = set()
    val = claims.get("scope")
    if isinstance(val, str):
        scopes |= {s.strip().upper().replace("SCOPE_", "") for s in val.split() if s.strip()}
    elif isinstance(val, (list, tuple)):
        scopes |= {str(s).strip().upper().replace("SCOPE_", "") for s in val}
    auth = claims.get("authorities")
    if isinstance(auth, (list, tuple)):
        scopes |= {str(s).strip().upper().replace("SCOPE_", "") for s in auth}
    return scopes

def require_scope(*required_scopes: str):
    required = {s.upper().replace("SCOPE_", "") for s in required_scopes}
    def decorator(func):
        @wraps(func)
        def wrapper(*args, **kwargs):
            claims = getattr(g, "jwt_claims", None)
            if not claims:
                return jsonify({"error": "no_claims"}), 401
            granted = _scopes_from_claims(claims)
            if not required.issubset(granted):
                return jsonify({
                    "error": "insufficient_scope",
                    "required": sorted(required),
                    "granted": sorted(granted)
                }), 403
            return func(*args, **kwargs)
        return wrapper
    return decorator

def get_user_id_from_token() -> Optional[Any]:
    claims = getattr(g, "jwt_claims", None)
    
    if claims:
        user_id = claims.get("sub") or claims.get("user_id") or claims.get("username")
        return user_id
    return None
