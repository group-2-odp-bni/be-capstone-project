import os
from math import floor
from bson import ObjectId
from datetime import datetime, timezone
from flask import Flask, request, jsonify, redirect
from utils.auth_util import require_auth, require_scope, get_user_id_from_token
from services.kafka_service import(publish_bill_created,publish_bill_reminded,publish_payment_intent)
from services.gemini_service import process_receipt_image
from services.mongo_service import (
    save_ocr_result,      
    create_bill,          
    get_bill_detail,  
    get_member_invoice,  
    list_history_owned,           
    list_history_assigned,        
    _to_jsonable,
    bills_collection
)

from services.shortlink_service import (
    create_short_tokens,          
    resolve_short_token,
    _APP_BASE as FE_APP_BASE         
)

from services.notification_service import (
    remind_unpaid_members,
)


app = Flask(__name__)



def _parse_amount_to_int_rp(val):
    if val is None:
        return 0
    if isinstance(val, int):
        return val
    if isinstance(val, float):
        return int(round(val))
    s = str(val)
    filtered = "".join(ch for ch in s if ch.isdigit())
    return int(filtered) if filtered else 0

def _normalize_items(extracted):
    raw_items = extracted.get("items") or []
    norm = []
    for idx, it in enumerate(raw_items, start=1):
        name = (it.get("nama_item") or it.get("name") or f"Item {idx}").strip()
        qty = int(it.get("kuantitas") or it.get("quantity") or it.get("qty") or 1)

        unit_price = _parse_amount_to_int_rp(it.get("unit_price") or it.get("price"))
        line_total = _parse_amount_to_int_rp(it.get("harga_total") or it.get("line_total") or it.get("subtotal"))

        if unit_price <= 0 and line_total > 0 and qty > 0:
            unit_price = line_total // qty
        if line_total <= 0:
            line_total = qty * unit_price

        norm.append({
            "line_id": f"L{idx:03d}",
            "name": name,
            "qty": qty,
            "unit_price_rp": unit_price,
            "line_subtotal_rp": line_total
        })
    return norm

def _compute_bill_components(extracted, items_norm):
    items_subtotal_rp = sum(i["line_subtotal_rp"] for i in items_norm)
    tax_rp     = _parse_amount_to_int_rp(extracted.get("tax") or extracted.get("pajak"))
    service_rp = _parse_amount_to_int_rp(extracted.get("service") or extracted.get("service_charge") or extracted.get("layanan"))
    tip_rp     = _parse_amount_to_int_rp(extracted.get("tip"))
    total_ext  = _parse_amount_to_int_rp(extracted.get("total") or extracted.get("harga_total_struk"))
    computed_total = items_subtotal_rp + tax_rp + service_rp + tip_rp
    total_rp = total_ext if total_ext > 0 else computed_total
    return {
        "items_subtotal_rp": items_subtotal_rp,
        "tax_rp": tax_rp,
        "service_rp": service_rp,
        "tip_rp": tip_rp,
        "total_rp": total_rp
    }

def _proportional_split(amount_rp, weights):
    if not weights:
        return {}
    total_w = sum(weights.values())
    if total_w <= 0:
        n = len(weights)
        base = amount_rp // n
        remainder = amount_rp - base * n
        keys = list(weights.keys())
        out = {k: base for k in keys}
        for i in range(remainder):
            out[keys[i]] += 1
        return out
    raw = {u: (amount_rp * w) / total_w for u, w in weights.items()}
    floored = {u: floor(v) for u, v in raw.items()}
    remainder = amount_rp - sum(floored.values())
    by_frac = sorted(((u, raw[u] - floored[u]) for u in raw), key=lambda x: x[1], reverse=True)
    for i in range(remainder):
        floored[by_frac[i][0]] += 1
    return floored

def _now_iso():
    return datetime.now(timezone.utc).isoformat()

@app.post("/api/v1/split-bill/extract-text")
@require_auth
@require_scope("FULL_ACCESS")
def extract_text():
    image_file = request.files.get("receipt_image")
    extra_text = request.form.get("extra_text")
    if not image_file and not (extra_text and extra_text.strip()):
        return jsonify(_to_jsonable({"error": True, "message": "Kirim 'receipt_image' atau 'extra_text'.", "data": None})), 400

    image_bytes = image_file.read() if image_file and image_file.filename.strip() else None
    ai_res = process_receipt_image(image_bytes, extra_text=extra_text)
    if ai_res.get("error"):
        status = 400 if "input" in (ai_res.get("message", "").lower()) else 502
        return jsonify(_to_jsonable(ai_res)), status

    extracted = ai_res.get("data") or {}
    items_norm = _normalize_items(extracted)
    comps = _compute_bill_components(extracted, items_norm)

    saved = save_ocr_result({
        "extracted": extracted,
        "items_norm": items_norm,
        "components": comps,
        "created_at": _now_iso(),
        "status": "DRAFT"
    })
    if saved.get("error"):
        return jsonify(_to_jsonable({"error": True, "message": "Ekstraksi OK, simpan gagal.", "data": {"extract": extracted}})), 207

    return jsonify(_to_jsonable({
        "error": False,
        "message": "Berhasil ekstraksi.",
        "data": {
            "ocr_id": saved["data"]["ocr_id"],
            "items_for_assignment": items_norm,
            "components": comps,
            "receipt_url": extracted.get("image_url")
        }
    })), 201


@app.post("/api/v1/split-bill/bills")
@require_auth
@require_scope("FULL_ACCESS")
def post_bills():
    user_id = get_user_id_from_token()
    payload = request.get_json(silent=True) or {}

    title = payload.get("title") or "Split Bill"
    destination_wallet_id = payload.get("destinationWalletId")
    items = payload.get("items") or []
    fees = payload.get("fees") or {}
    assignments = payload.get("assignments") or []
    receipt_url = payload.get("imageUrl")
    tax_strategy = (payload.get("tax_strategy") or "proportional").lower()

    if not destination_wallet_id or not items or not assignments:
        return jsonify(_to_jsonable({"error": True, "message": "destinationWalletId, items, assignments wajib.", "data": None})), 400
    expanded_items = payload.get("expandedItems") or []
    extracted = {"items": items, "tax": fees.get("tax"), "service": fees.get("service"),
                 "tip": fees.get("tip") or fees.get("other"), "total": fees.get("total")}
    items_norm = _normalize_items(extracted)
    comps = _compute_bill_components(extracted, items_norm)

    bill_doc = {
        "title": title,
        "creator_user_id": user_id,
        "destination_wallet_id": destination_wallet_id,
        "items_norm": items_norm,
        "components": comps,
        "assignments": assignments,
        "expanded_items": expanded_items,
        "tax_strategy": tax_strategy,
        "receipt_url": receipt_url,
        "status": "SENT",
        "created_at": _now_iso(),
        "updated_at": _now_iso(),
    }
    res = create_bill(bill_doc, assignments)
    if res.get("error"):
        return jsonify(_to_jsonable(res)), 400
    result_data = res.get("data")

    bill_id = res["data"]["bill_id"]
    members_data = res["data"]["members"]
    tokens = create_short_tokens(bill_id=bill_id, owner_user_id=user_id, members=res["data"]["members"])
    owner_short_link = tokens["owner_link"]      

    member_links = tokens["member_links"]
    if bills_collection is not None:
        link_map = {ml["memberId"]: ml.get("shortLink") for ml in (member_links or [])}
        doc = bills_collection.find_one({"_id": ObjectId(bill_id)})
        if doc and doc.get("members"):
            new_members = []
            for m in doc["members"]:
                mid = m.get("member_id")
                sl = link_map.get(mid) or m.get("short_link")
                nm = dict(m)
                nm["short_link"] = sl
                new_members.append(nm)
            bills_collection.update_one(
                {"_id": ObjectId(bill_id)},
                {"$set": {"members": new_members, "updated_at": datetime.utcnow()}}
            )
    try:
        publish_bill_created(
            bill_id=bill_id,
            owner_user_id=user_id,
            owner_link=owner_short_link,
            member_links=member_links,
        )
    except Exception as e:
        print(f"WARNING: Failed to publish Kafka event for bill {bill_id}. Error: {e}")
    return jsonify(_to_jsonable({
        "error": False,
        "message": "Bill dibuat.",
        "data": {
            "billId": bill_id,
            "status": "SENT",
            "ownerShortLink": owner_short_link,
            "memberLinks": member_links
        }
    })), 201


@app.get("/api/v1/split-bill/bills/<bill_id>")
@require_auth
@require_scope("FULL_ACCESS")
def get_bill_owner(bill_id):
    user_id = get_user_id_from_token()
    res = get_bill_detail(bill_id, viewer_user_id=user_id)
    status = 200 if not res.get("error") else (404 if "tidak ditemukan" in (res.get("message","").lower()) else 400)
    return jsonify(_to_jsonable(res)), status


@app.get("/api/v1/split-bill/bills/<bill_id>/members/<member_id>")
@require_auth
@require_scope("FULL_ACCESS")
def get_member_invoice_endpoint(bill_id, member_id):
    user_id = get_user_id_from_token()
    res = get_member_invoice(bill_id, member_id, viewer_user_id=user_id)
    status = 200 if not res.get("error") else (404 if "tidak ditemukan" in (res.get("message","").lower()) else 400)
    return jsonify(_to_jsonable(res)), status


@app.post("/api/v1/split-bill/bills/<bill_id>/remind")
@require_auth
@require_scope("FULL_ACCESS")
def remind_unpaid(bill_id):
    user_id = get_user_id_from_token()
    payload = request.get_json(silent=True) or {}
    channels = payload.get("channels") or ["wa"]
    res = remind_unpaid_members(bill_id=bill_id, actor_user_id=user_id, channels=channels)
    if res.get("error"):
        return jsonify(_to_jsonable(res)), 400
    result_data = res.get("data")
    try:
        publish_bill_reminded(
            bill_id=bill_id,
            actor_user_id=user_id,
            channels=result_data.get("requestedChannels"),
            member_links=result_data.get("memberLinks"),
            result_data=result_data.get("result")
        )
    except Exception as e:
        print(f"WARNING: Failed to publish Kafka event for remind {bill_id}. Error: {e}")
    return jsonify(_to_jsonable({"error": False, "message": "Pengingat diproses.", "data": res.get("data")})), 202


@app.post("/api/v1/split-bill/bills/<bill_id>/members/<member_id>/pay-intent")
@require_auth
@require_scope("FULL_ACCESS")
def new_pay_intent(bill_id, member_id):
    user_id = get_user_id_from_token()
    payload = request.get_json(silent=True) or {}
    source_wallet_id = payload.get("sourceWalletId")
    if not source_wallet_id:
        return jsonify(_to_jsonable({"error": True, "message": "sourceWalletId wajib.", "data": None})), 400

    member_invoice = get_member_invoice(bill_id, member_id, viewer_user_id=user_id)
    if member_invoice.get("error"):
        return jsonify(_to_jsonable(member_invoice)), 400
        
    amount_due = member_invoice["data"]["totalDue"]
    destination_wallet_id = member_invoice["data"]["payTo"]["walletId"]

    try:
        publish_payment_intent(
            bill_id=bill_id,
            member_id=member_id,
            amount=amount_due,
            source_wallet_id=source_wallet_id,
            destination_wallet_id=destination_wallet_id
        )
    except Exception as e:
        print(f"WARNING: Failed to publish Kafka event for pay-intent {bill_id}. Error: {e}")
        return jsonify(_to_jsonable({"error": True, "message": "Gagal memproses pembayaran.", "data": None})), 500
    return jsonify(_to_jsonable({
        "error": False,
        "message": "Permintaan pembayaran diterima.",
        "data": {"status": "PENDING_PROCESS"}
    })), 202



@app.get("/s/<token>")
def short_resolver(token):
    try:
        info = resolve_short_token(token)
    except Exception as e:
        return jsonify({"error": True, "message": f"Token invalid: {e}"}), 403
    base_url = FE_APP_BASE.rstrip("/")
    if info["type"] == "owner":
        target_url = f"{base_url}/app/splitbill/{info['billId']}"
        return redirect(target_url, code=302)
    elif info["type"] == "member":
        target_url = f"{base_url}/app/splitbill/{info['billId']}/member/{info['memberId']}"
        return redirect(target_url, code=302)
        
    return jsonify({"error": True, "message": "Token type tidak diketahui"}), 400

@app.get("/api/v1/split-bill/history")
@require_auth
@require_scope("FULL_ACCESS")
def history():
    user_id = get_user_id_from_token()
    view = (request.args.get("view") or "owned").lower()  # owned|assigned
    status_f = request.args.get("status")  
    q = request.args.get("q")
    t_from = request.args.get("from")
    t_to = request.args.get("to")
    limit = int(request.args.get("limit") or 20)
    cursor = request.args.get("cursor")

    filters = {
        "status": status_f, "q": q, "from": t_from, "to": t_to, "limit": limit, "cursor": cursor
    }
    if view == "assigned":
        res = list_history_assigned(user_id, filters)
    else:
        res = list_history_owned(user_id, filters)

    if res.get("error"):
        return jsonify(_to_jsonable(res)), 400
    return jsonify(_to_jsonable({"error": False, "message": "OK", "data": res.get("data")})), 200

@app.post("/api/v1/process-receipt")
@require_auth
@require_scope("FULL_ACCESS")
def _compat_process_receipt():
    return extract_text()

@app.post("/api/v1/submit-split")
@require_auth
@require_scope("FULL_ACCESS")
def _compat_submit_split():
    return post_bills()

@app.get("/health")
def health_check():
    """Health check endpoint for Docker and load balancers"""
    return jsonify({
        "status": "healthy",
        "service": "ml-engine",
        "timestamp": _now_iso()
    }), 200

@app.get("/actuator/health")
def actuator_health():
    """Spring Boot style health check for consistency with Java services"""
    return jsonify({
        "status": "UP",
        "components": {
            "mongoDb": {"status": "UP" if bills_collection is not None else "DOWN"},
            "kafka": {"status": "UP"}
        }
    }), 200

def _recompute_bill_status(doc: dict) -> str:
    statuses = [m.get("status") for m in (doc.get("members") or [])]
    if statuses and all(s == "PAID" for s in statuses):
        return "PAID"
    if any(s == "PAID" for s in statuses):
        return "PARTIALLY_PAID"
    return "SENT"
@app.post("/api/v1/split-bill/bills/<bill_id>/mark-paid-batch")
@require_auth
@require_scope("FULL_ACCESS")
def mark_members_paid_batch(bill_id):
    user_id = get_user_id_from_token()
    payload = request.get_json(silent=True) or {}
    member_ids_to_update = payload.get("member_ids")
    if not member_ids_to_update or not isinstance(member_ids_to_update, list):
        return jsonify(_to_jsonable({"error": True, "message": "Payload 'member_ids' (list) wajib.", "data": None})), 400
    if bills_collection is None: 
        return jsonify(_to_jsonable({"error": True, "message": "Database tidak siap.", "data": None})), 500

    try:
        oid = ObjectId(bill_id)
    except Exception:
        return jsonify(_to_jsonable({"error": True, "message": "Bill ID tidak valid.", "data": None})), 400

    doc = bills_collection.find_one({"_id": oid})
    if not doc:
        return jsonify(_to_jsonable({"error": True, "message": "Tagihan tidak ditemukan.", "data": None})), 404

    if doc.get("creator_user_id") != user_id:
        return jsonify(_to_jsonable({"error": True, "message": "Akses ditolak.", "data": None})), 403

    members = doc.get("members", [])
    updated_paid_total = int(doc.get("paid_total", 0))
    members_updated_count = 0

    for m in members:
        if m.get("member_id") in member_ids_to_update and m.get("status") != "PAID":
            amount_due = int(m.get("amount_due", 0))
            current_paid = int(m.get("paid", 0))
            
            updated_paid_total += (amount_due - current_paid)
            
            m["status"] = "PAID"
            m["paid"] = amount_due 
            m["payment_method"] = "MANUAL_CONFIRMATION"
            members_updated_count += 1
    
    if members_updated_count == 0:
         return jsonify(_to_jsonable({"error": False, "message": "Tidak ada member yang diupdate (mungkin sudah lunas).", "data": None})), 200

    new_bill_status = _recompute_bill_status({"members": members}) 

    bills_collection.update_one(
        {"_id": oid},
        {
            "$set": {
                "members": members,
                "paid_total": updated_paid_total,
                "status": new_bill_status,
                "updated_at": datetime.utcnow()
            }
        }
    )

    return jsonify(_to_jsonable({
        "error": False, 
        "message": f"{members_updated_count} member berhasil diupdate.", 
        "data": {
            "billId": bill_id,
            "newBillStatus": new_bill_status,
            "newPaidTotal": updated_paid_total
        }
    })), 200
if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.getenv("PORT", 5000)), debug=True)
