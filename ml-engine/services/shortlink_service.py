import os
import hmac
import base64
import hashlib
from datetime import datetime, timedelta, timezone
from typing import Dict, Any, List

from bson import ObjectId
from pymongo.errors import PyMongoError

from services.mongo_service import db, shortlinks_collection
_APP_BASE = os.getenv("APP_BASE_URL", "https://app.orangebybni.my.id")
_SECRET = os.getenv("SHORTLINK_SECRET", "dev-secret")
_DEFAULT_TTL_MIN = int(os.getenv("SHORTLINK_TTL_MIN", "4320"))
def _abs(path: str) -> str:
    if not path: return path
    if path.startswith("http://") or path.startswith("https://"): return path
    return _APP_BASE.rstrip("/") + "/" + path.lstrip("/")

def _sign(payload: str) -> str:
    sig = hmac.new(_SECRET.encode(), payload.encode(), hashlib.sha256).digest()
    return base64.urlsafe_b64encode(sig).decode().rstrip("=")

def _token(type_: str, bill_id: str, member_id: str | None, exp: datetime) -> str:
    payload = f"{type_}.{bill_id}.{member_id or ''}.{int(exp.timestamp())}"
    sig = _sign(payload)
    return base64.urlsafe_b64encode(payload.encode()).decode().rstrip("=") + "." + sig

def create_short_tokens(bill_id: str, owner_user_id: str, members: List[Dict[str, Any]]) -> Dict[str, Any]:
    if shortlinks_collection is None:
        raise RuntimeError("Mongo shortlinks tidak siap")

    exp = datetime.now(timezone.utc) + timedelta(minutes=_DEFAULT_TTL_MIN)
    exp_ts = int(exp.timestamp())
    owner_tok = _token("owner", bill_id, None, exp)

    member_links = []
    docs = [{"token": owner_tok, "type": "owner", "bill_id": bill_id, "member_id": None,
                "user_id": owner_user_id, 
                "exp": exp_ts, "created_at": datetime.now(timezone.utc)}]

    for m in members:
        mid = m.get("memberId") or m.get("member_id")
        uid = m.get("userId") or m.get("user_id")
        tok = _token("member", bill_id, mid, exp)
        phone = m.get("phone_e164")

        member_links.append({
                "memberId": mid,
                "userId": uid,
                "shortLink": _abs(f"/s/{tok}"),
                "phoneE164": phone
                })
        docs.append({"token": tok, "type": "member", "bill_id": bill_id,
                            "member_id": mid, "user_id": uid, 
                            "exp": exp_ts, "created_at": datetime.now(timezone.utc)})

    try:
        shortlinks_collection.insert_many(docs, ordered=False)
    except PyMongoError:
        pass
    return {"owner_link": _abs(f"/s/{owner_tok}"), "member_links": member_links}

def resolve_short_token(token: str) -> Dict[str, Any]:
    if shortlinks_collection is None:
        raise RuntimeError("Mongo shortlinks tidak siap")
    try:
        token_payload_b64, token_sig = token.split(".", 1)
        payload = base64.urlsafe_b64decode(token_payload_b64 + "==").decode()
        if not hmac.compare_digest(_sign(payload), token_sig):
            raise ValueError("signature mismatch")
        type_, bill_id, member_id, exp_ts = payload.split(".", 3)
        exp_ts = int(exp_ts)
    except Exception as e:
        raise ValueError(f"token invalid: {e}")

    doc = shortlinks_collection.find_one({"token": token})
    if not doc:
        raise ValueError("token tidak dikenali")
    if doc["exp"] != exp_ts:
        raise ValueError("exp tidak sinkron")
    if datetime.now(timezone.utc).timestamp() > exp_ts:
        raise ValueError("token expired")

    return {"type": doc["type"], "billId": doc["bill_id"], "memberId": doc.get("member_id"),
            "userId": doc.get("user_id")}
