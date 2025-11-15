from typing import Dict, Any, List
from bson import ObjectId
from services.mongo_service import bills_collection

def remind_unpaid_members(bill_id: str, actor_user_id: str, channels: List[str]) -> Dict[str, Any]:
    doc = bills_collection.find_one({"_id": ObjectId(bill_id)})
    if not doc:
        return {"error": True, "message": "Bill tidak ditemukan", "data": None}

    members = doc.get("members") or []
    unpaid = [m for m in members if (m.get("status") or "").upper() != "PAID"]

    member_links: List[Dict[str, Any]] = []
    skipped = 0
    for m in unpaid:
        user_id = m.get("user_id") or m.get("userId")
        member_id = m.get("member_id") or m.get("memberId")
        short_link = m.get("short_link") or m.get("shortLink")

        if not user_id or not short_link:
            skipped += 1
            continue

        member_links.append({
            "userId": user_id,
            "memberId": member_id,
            "shortLink": short_link
        })

    result = {
        "counts": {"totalMembers": len(members), "unpaid": len(unpaid)},
        "summary": {"success": 0, "fail": 0}
    }

    data = {
        "requestedChannels": channels,
        "memberLinks": member_links,  
        "result": result,
        "skipped": skipped            
    }
    return {"error": False, "message": "OK", "data": data}