import os
from datetime import datetime, date, timezone
from decimal import Decimal
from typing import Dict, Any, List, Optional

from pymongo import MongoClient, ReturnDocument, ASCENDING, DESCENDING
from pymongo.errors import PyMongoError, ConfigurationError, ServerSelectionTimeoutError
from bson import ObjectId

_MONGO_URI = os.getenv("MONGODB_URI") or os.getenv("MONGO_URI")
_MONGO_DB = os.getenv("MONGODB_DB") or os.getenv("MONGO_DB_NAME") or "appdb"

def _utcnow_iso():
    return datetime.now(timezone.utc).isoformat()
def _normalize_e164(phone: str | None) -> str | None:
    if not phone: return None
    p = str(phone).strip().replace(" ", "")
    if p.startswith("0"): p = "+62" + p[1:]
    if not p.startswith("+"): p = "+" + p
    return p
def _init_client():
    if not _MONGO_URI:
        raise ConfigurationError("MONGODB_URI/MONGO_URI tidak diset di environment.")
    client = MongoClient(
        _MONGO_URI,
        serverSelectionTimeoutMS=5000,
        connectTimeoutMS=5000,
        socketTimeoutMS=10000,
        uuidRepresentation="standard",
    )
    client.admin.command("ping")
    return client

try:
    client = _init_client()
    db = client[_MONGO_DB]
    bills_collection = db["bills"]
    shortlinks_collection = db["shortlinks"]
    payment_events_collection = db["payment_events"]

    bills_collection.create_index([("creator_user_id", ASCENDING), ("created_at", DESCENDING)])
    bills_collection.create_index([("members.user_id", ASCENDING)])
    shortlinks_collection.create_index([("token", ASCENDING)], unique=True)
    payment_events_collection.create_index([("payment_id", ASCENDING)], unique=True)
except (ConfigurationError, ServerSelectionTimeoutError, PyMongoError):
    client = None
    db = None
    bills_collection = None
    shortlinks_collection = None
    payment_events_collection = None

def _to_jsonable(obj):
    if isinstance(obj, ObjectId):
        return str(obj)
    if isinstance(obj, (datetime, date)):
        return obj.isoformat()
    if isinstance(obj, Decimal):
        return float(obj)
    if isinstance(obj, dict):
        return {k: _to_jsonable(v) for k, v in obj.items()}
    if isinstance(obj, (list, tuple, set)):
        return [_to_jsonable(x) for x in obj]
    return obj


def save_ocr_result(ocr_doc: Dict[str, Any]) -> Dict[str, Any]:
    if bills_collection is None:
        return {"error": True, "message": "Mongo tidak siap", "data": None}
    try:
        doc = dict(ocr_doc or {})
        doc["created_at"] = datetime.utcnow()
        doc["status"] = doc.get("status") or "DRAFT"
        res = bills_collection.insert_one(doc)
        return {"error": False, "message": "OK", "data": {"ocr_id": str(res.inserted_id)}}
    except PyMongoError as e:
        return {"error": True, "message": "Gagal menyimpan OCR.", "data": {"exception": str(e)}}

def _derive_members_from_assignments(assignments: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    members = []
    for a in assignments:
        ref = a.get("memberRef") or {}
        user_id = ref.get("userId")
        phone_e164 = _normalize_e164(ref.get("phone") or ref.get("phoneE164"))
        amount = int(a.get("amount") or 0)
        members.append({
            "member_id": str(ObjectId()),
            "member_ref": ref,
            "user_id": user_id,            
            "phone_e164": phone_e164,      
            "short_link": None,            
            "status": "PENDING",
            "amount_due": amount if amount > 0 else None, 
            "paid": 0,
            "items": a.get("items") or []  
        })
    return members

def create_bill(bill_doc: Dict[str, Any], assignments: List[Dict[str, Any]]) -> Dict[str, Any]:
    if bills_collection is None:
        return {"error": True, "message": "Mongo tidak siap", "data": None}
    try:
        members = _derive_members_from_assignments(assignments)
        bill = dict(bill_doc or {})
        bill["members"] = members
        bill["assignments"] = assignments
        bill["paid_total"] = 0
        bill["status"] = bill.get("status") or "SENT"
        bill["created_at"] = datetime.utcnow()
        bill["updated_at"] = datetime.utcnow()
        res = bills_collection.insert_one(bill)
        return {
            "error": False,
            "message": "OK",
            "data": {
                "bill_id": str(res.inserted_id),
                "members": [{
                            "memberId": m["member_id"],
                            "userId": m.get("user_id"),
                            "phone_e164": m.get("phone_e164")
                            } for m in members]
            }
        }
    except PyMongoError as e:
        return {"error": True, "message": "Gagal membuat bill.", "data": {"exception": str(e)}}


def _aggregate_totals(doc: Dict[str, Any]) -> Dict[str, Any]:
    comps = doc.get("components") or {}
    total = int(comps.get("total_rp") or 0)
    paid_total = int(doc.get("paid_total") or 0)
    return {"total": total, "paidTotal": paid_total, "subtotal": int(comps.get("items_subtotal_rp") or 0)}

def get_bill_detail(bill_id: str, viewer_user_id: str) -> Dict[str, Any]:
    if bills_collection is None:
        return {"error": True, "message": "Mongo tidak siap", "data": None}
    try:
        oid = ObjectId(bill_id)
    except Exception:
        return {"error": True, "message": "billId tidak valid", "data": {"billId": bill_id}}
    doc = bills_collection.find_one({"_id": oid})
    if not doc:
        return {"error": True, "message": "Bill tidak ditemukan", "data": None}
    owner_id = doc.get("creator_user_id")
    members_list = doc.get("members") or []
    member_user_ids = [
        m.get("user_id") or m.get("member_ref", {}).get("user_id") 
        for m in members_list
    ]
    is_authorized = viewer_user_id == owner_id   
    if not is_authorized:
        return {
            "error": True, 
            "message": "Akses ditolak. Anda tidak terdaftar di bill ini.", 
            "data": None
        }
    totals = _aggregate_totals(doc)
    members = []
    for m in members_list:
        members.append({
            "memberId": m["member_id"],
            "name": m.get("member_ref", {}).get("name") or m.get("member_ref", {}).get("phone") or m.get("member_ref", {}).get("email"),
            "amount": m.get("amount_due"),
            "status": m.get("status"),
            "paid": m.get("paid", 0),
            "initial": (m.get("member_ref", {}).get("name") or "?")[0].upper()
        })
    unpaid_count = sum(1 for m in members if m.get("status") != "PAID")
    out = {
        "billId": bill_id,
        "title": doc.get("title"),
        "creatorUserId": doc.get("creator_user_id"),
        "destinationWalletId": doc.get("destination_wallet_id"),
        "imageUrl": doc.get("receipt_url"),
        "status": doc.get("status"),
        "items": doc.get("expanded_items") or doc.get("items_norm"),        "fees": {
            "tax": (doc.get("components") or {}).get("tax_rp", 0),
            "service": (doc.get("components") or {}).get("service_rp", 0),
            "tip": (doc.get("components") or {}).get("tip_rp", 0)
        },
        "totals": totals,
        "members": members,
        "unpaidCount": unpaid_count
    }
    return {"error": False, "message": "OK", "data": out}


def get_member_invoice(bill_id: str, member_id: str, viewer_user_id: str) -> Dict[str, Any]:
    if bills_collection is None:
        return {"error": True, "message": "Mongo tidak siap", "data": None}
    try:
        oid = ObjectId(bill_id)
    except Exception:
        return {"error": True, "message": "billId tidak valid", "data": {"billId": bill_id}}

    doc = bills_collection.find_one({"_id": oid})
    if not doc:
        return {"error": True, "message": "Bill tidak ditemukan", "data": None}
    owner_id = doc.get("creator_user_id")
    m = next((m for m in (doc.get("members") or []) if m.get("member_id") == member_id), None)
    if not m:
        return {"error": True, "message": "Member tidak ditemukan", "data": None}
    member_user_id = m.get("user_id") or m.get("member_ref", {}).get("user_id")
    is_owner = (viewer_user_id == owner_id)
    is_the_member = (member_user_id and viewer_user_id == member_user_id)
    if not (is_owner or is_the_member):
        return {
            "error": True, 
            "message": "Akses ditolak. Invoice ini bukan milik Anda.", 
            "data": None
        }
    member_ref = m.get("member_ref") or {}
    member_profile = {
        "name": member_ref.get("name") or member_ref.get("phone") or "Member",
        "phone": member_ref.get("phone"),
        "initial": (member_ref.get("name") or "?")[0].upper()
    }
    bill_comps = doc.get("components") or {}
    total_items_subtotal = int(bill_comps.get("items_subtotal_rp") or 0)
    my_items = m.get("items") or []
    member_subtotal = 0
    for it in my_items:
        val = it.get("line_subtotal_rp") or it.get("total") or it.get("price", 0) * it.get("qty", 1)
        member_subtotal += int(val)
    ratio = member_subtotal / total_items_subtotal if total_items_subtotal > 0 else 0

    fees_share = {
        "tax": int(round((bill_comps.get("tax_rp") or 0) * ratio)),
        "service": int(round((bill_comps.get("service_rp") or 0) * ratio)),
        "other": int(round((bill_comps.get("tip_rp") or 0) * ratio)),
    }

    total_due = int(m.get("amount_due") or 0)

    out = {
        "billId": bill_id,
        "memberId": member_id,
        "title": doc.get("title"),
        "receiptUrl": doc.get("receipt_url"), 
        "payTo": {
            "walletId": doc.get("destination_wallet_id"),
            "userId": doc.get("creator_user_id"),
        },        
        "amount": total_due,
        "status": m.get("status"),
        "memberProfile": member_profile, 
        "myItems": my_items,
        "feesShare": fees_share, 
        "totalDue": total_due,
    }

    return {"error": False, "message": "OK", "data": out}

def update_bill_split_data(bill_id: str, split_doc: Dict[str, Any]) -> Dict[str, Any]:
    if bills_collection is None:
        return {"error": True, "message": "Mongo tidak siap", "data": None}
    try:
        oid = ObjectId(bill_id)
    except Exception:
        return {"error": True, "message": "billId tidak valid", "data": {"billId": bill_id}}
    try:
        res = bills_collection.find_one_and_update(
            {"_id": oid},
            {"$set": {"split": split_doc, "updated_at": datetime.utcnow()}},
            return_document=ReturnDocument.AFTER
        )
        if not res:
            return {"error": True, "message": "Bill tidak ditemukan", "data": None}
        return {"error": False, "message": "OK", "data": {"billId": str(res["_id"])}}
    except PyMongoError as e:
        return {"error": True, "message": "Gagal update split", "data": {"exception": str(e)}}


def _recompute_bill_status(doc: Dict[str, Any]) -> str:
    """PAID jika semua member status=PAID; PARTIALLY_PAID jika ada sebagian; SENT jika none paid"""
    statuses = [m.get("status") for m in (doc.get("members") or [])]
    if statuses and all(s == "PAID" for s in statuses):
        return "PAID"
    if any(s == "PAID" for s in statuses):
        return "PARTIALLY_PAID"
    return "SENT"

def append_payment_event(evt: Dict[str, Any]) -> Dict[str, Any]:
    if bills_collection is None or payment_events_collection is None:
        return {"error": True, "message": "Mongo tidak siap", "data": None}

    payment_id = evt.get("paymentId")
    bill_id = evt.get("billId")
    member_id = evt.get("memberId")
    status = evt.get("status")
    amount = int(evt.get("amount") or 0)

    if not payment_id or not bill_id or not member_id:
        return {"error": True, "message": "Payload tidak lengkap", "data": None}

    try:
        payment_events_collection.insert_one({
            "payment_id": payment_id,
            "bill_id": bill_id,
            "member_id": member_id,
            "status": status,
            "amount": amount,
            "received_at": datetime.utcnow()
        })
    except Exception:
        return {"error": False, "message": "Duplicate webhook ignored", "data": {"idempotent": True}}
    try:
        oid = ObjectId(bill_id)
    except Exception:
        return {"error": True, "message": "billId tidak valid", "data": {"billId": bill_id}}

    doc = bills_collection.find_one({"_id": oid})
    if not doc:
        return {"error": True, "message": "Bill tidak ditemukan", "data": None}

    members = doc.get("members") or []
    updated_paid_total = int(doc.get("paid_total") or 0)
    for m in members:
        if m.get("member_id") == member_id:
            if status == "CAPTURED":
                if m.get("status") != "PAID": 
                    m["status"] = "PAID"
                    m["paid"] = int(m.get("paid") or 0) + amount
                    updated_paid_total += amount
            elif status == "FAILED":
                m["last_error"] = "FAILED"
            break

    new_status = _recompute_bill_status({**doc, "members": members})
    bills_collection.update_one(
        {"_id": oid},
        {"$set": {"members": members, "paid_total": updated_paid_total, "status": new_status, "updated_at": datetime.utcnow()}}
    )
    return {"error": False, "message": "OK", "data": {"billStatus": new_status}}

def list_history_owned(user_id: str, filters: Dict[str, Any]) -> Dict[str, Any]:
    if bills_collection is None:
        return {"error": True, "message": "Mongo tidak siap", "data": None}

    query = {"creator_user_id": user_id}
    status_f = filters.get("status")
    if status_f and status_f != "ALL":
        query["status"] = status_f
    if filters.get("q"):
        query["title"] = {"$regex": filters["q"], "$options": "i"}

    limit = int(filters.get("limit") or 20)
    cursor = filters.get("cursor")
    if cursor:
        try:
            query["_id"] = {"$lt": ObjectId(cursor)}
        except Exception:
            pass

    docs = list(bills_collection.find(query).sort([("_id", DESCENDING)]).limit(limit))
    items = []
    for d in docs:
        comps = d.get("components") or {}
        items.append({
            "billId": str(d["_id"]),
            "title": d.get("title"),
            "createdAt": d.get("created_at").isoformat() if d.get("created_at") else None,
            "total": int(comps.get("total_rp") or 0),
            "paidTotal": int(d.get("paid_total") or 0),
            "memberCount": len(d.get("members") or []),
            "status": d.get("status"),
            "unpaidCount": sum(1 for m in (d.get("members") or []) if m.get("status") != "PAID"),
        })
    next_cursor = str(docs[-1]["_id"]) if docs else None
    return {"error": False, "message": "OK", "data": {"items": items, "nextCursor": next_cursor}}

def list_history_assigned(user_id: str, filters: Dict[str, Any]) -> Dict[str, Any]:
    if bills_collection is None:
        return {"error": True, "message": "Mongo tidak siap", "data": None}
    query = {"members.member_ref.userId": user_id}
    status_f = filters.get("status")
    if status_f and status_f != "ALL":
        query["members.status"] = status_f

    limit = int(filters.get("limit") or 20)
    cursor = filters.get("cursor")
    if cursor:
        try:
            query["_id"] = {"$lt": ObjectId(cursor)}
        except Exception:
            pass

    docs = list(bills_collection.find(query).sort([("_id", DESCENDING)]).limit(limit))
    items = []
    for d in docs:
        mine = next((m for m in (d.get("members") or []) if (m.get("member_ref") or {}).get("userId") == user_id), None)
        if not mine:
            continue
        comps = d.get("components") or {}
        items.append({
            "billId": str(d["_id"]),
            "memberId": mine.get("member_id"),
            "title": d.get("title"),
            "ownerName": d.get("creator_user_id"), 
            "myAmount": int(mine.get("amount_due") or 0),
            "myStatus": mine.get("status"),
            "createdAt": d.get("created_at").isoformat() if d.get("created_at") else None,
        })
    next_cursor = str(docs[-1]["_id"]) if docs else None
    return {"error": False, "message": "OK", "data": {"items": items, "nextCursor": next_cursor}}
