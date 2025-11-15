import os
import io
import re
import json
import logging
from typing import Optional, Union, Dict, Any, List
from PIL import Image, UnidentifiedImageError
import google.generativeai as genai
from utils.prompt_template import PROMPT_TEXT
from google.cloud import storage
import uuid
GCS_BUCKET_NAME = "orange-pay-cloud-storage"
logger = logging.getLogger(__name__)
if not logger.handlers:
    _handler = logging.StreamHandler()
    _fmt = logging.Formatter(
        fmt="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S"
    )
    _handler.setFormatter(_fmt)
    logger.addHandler(_handler)
logger.setLevel(logging.DEBUG if os.getenv("LOG_LEVEL", "").upper() == "DEBUG" else logging.INFO)

_API_KEY = os.getenv("GOOGLE_API_KEY")
if not _API_KEY:
    logger.error("GOOGLE_API_KEY is not set. Please export it.")
else:
    genai.configure(api_key=_API_KEY)

def _ok(data: Any = None, message: str = "OK") -> Dict[str, Any]:
    return {"error": False, "message": message, "data": data}

def _fail(message: str, data: Any = None) -> Dict[str, Any]:
    return {"error": True, "message": message, "data": data}

def _build_multimodal_payload(prompt_text: str,
                              image: Optional[Image.Image] = None,
                              extra_text: Optional[str] = None) -> List[Union[str, Image.Image]]:
    parts: List[Union[str, Image.Image]] = []
    parts.append((prompt_text or "Extract the receipt data and return JSON only.").strip())
    if extra_text and extra_text.strip():
        parts.append(extra_text.strip())
    if image is not None:
        parts.append(image)
    return parts

def _extract_response_text(response) -> str:
    try:
        if hasattr(response, "candidates") and response.candidates:
            for cand in response.candidates:
                if getattr(cand, "content", None) and getattr(cand.content, "parts", None):
                    texts = []
                    for p in cand.content.parts:
                        t = getattr(p, "text", None)
                        if t:
                            texts.append(t)
                    if texts:
                        joined = "\n".join(texts).strip()
                        if joined:
                            return joined
    except Exception as e:
        logger.debug(f"_extract_response_text traverse candidates failed: {type(e).__name__}: {e}")
    if hasattr(response, "text") and isinstance(response.text, str) and response.text.strip():
        return response.text.strip()
    raise ValueError("Tidak ada teks yang bisa diambil dari response Gemini.")

def _extract_json_blob(text: str) -> str:
    cleaned = text.replace("```json", "```").strip()
    cleaned = cleaned.replace("```", "").strip()
    try:
        json.loads(cleaned)
        return cleaned
    except Exception:
        pass
    match = re.search(r'\{(?:[^{}]|(?R))*\}', cleaned, re.DOTALL)
    if match:
        return match.group(0)
    match_simple = re.search(r'\{.*\}', cleaned, re.DOTALL)
    if match_simple:
        return match_simple.group(0)
    raise ValueError("Tidak ditemukan blok JSON yang valid pada keluaran model.")

def process_receipt_image(image_bytes: Optional[bytes] = None, *,
                          model_name: str = "models/gemini-2.5-flash",
                          prompt_text: Optional[str] = None,
                          extra_text: Optional[str] = None) -> Dict[str, Any]:
    if not _API_KEY:
        return _fail("GOOGLE_API_KEY tidak terpasang di environment.", None)
    if image_bytes is None and not (extra_text or prompt_text or PROMPT_TEXT):
        return _fail("Tidak ada input yang diproses. Beri image_bytes atau teks.", None)
    gcs_url = None
    if image_bytes:
        gcs_url = _upload_to_gcs(image_bytes, content_type='image/jpeg')
    try:
        generation_config = {"response_mime_type": "application/json"}
        model = genai.GenerativeModel(model_name, generation_config=generation_config)
        logger.debug(f"Initialized GenerativeModel: {model_name} with JSON mime.")
    except TypeError:
        model = genai.GenerativeModel(model_name)
        logger.debug(f"Initialized GenerativeModel: {model_name} (no JSON mime).")
    except Exception as e:
        logger.error(f"Gagal inisialisasi model {model_name}: {type(e).__name__}: {e}")
        return _fail(f"Gagal inisialisasi model {model_name}.", {"exception": str(e)})

    image_pil = None
    if image_bytes is not None:
        try:
            image_pil = Image.open(io.BytesIO(image_bytes))
            image_pil.verify()
            image_pil = Image.open(io.BytesIO(image_bytes))
            logger.debug(f"Gambar terdeteksi: mode={image_pil.mode}, size={image_pil.size}")
        except UnidentifiedImageError:
            return _fail("Gambar tidak valid atau format tidak didukung.", None)
        except Exception as e:
            return _fail("Terjadi kesalahan saat membaca gambar.", {"exception": str(e)})

    final_prompt = (prompt_text or PROMPT_TEXT or "").strip()
    parts = _build_multimodal_payload(final_prompt, image=image_pil, extra_text=extra_text)
    try:
        response = model.generate_content(parts)
    except Exception as e:
        se = str(e)
        hint = None
        if "404" in se and "not found" in se.lower():
            hint = "Model tidak tersedia. Coba models/gemini-2.5-flash atau upgrade SDK."
        elif "429" in se or "rate" in se.lower():
            hint = "Rate limit/quota. Coba ulang beberapa saat lagi."
        elif "UNAUTHENTICATED" in se or "API key" in se:
            hint = "Periksa GOOGLE_API_KEY / project akses."
        return _fail("Gagal memanggil layanan Gemini.", {"exception": se, "hint": hint})

    try:
        resp_text = _extract_response_text(response)
    except Exception as e:
        return _fail("Tidak ada teks yang bisa diambil dari response Gemini.", {"exception": str(e)})

    try:
        try:
            data = json.loads(resp_text)
        except json.JSONDecodeError:
            json_blob = _extract_json_blob(resp_text)
            data = json.loads(json_blob)

        data.setdefault("items", [])
        data.setdefault("tax", 0)
        data.setdefault("service", 0)
        data.setdefault("tip", 0)
        data.setdefault("total", 0)
        data["image_url"] = gcs_url
        return _ok(data=data, message="Berhasil ekstraksi data.")
    except json.JSONDecodeError as e:
        preview = resp_text[:400]
        return _fail("Gagal mem-parsing JSON dari respons Gemini.", {"preview": preview})
    except Exception as e:
        return _fail("Respons model tidak berisi JSON yang valid.", {"exception": str(e)})
def _upload_to_gcs(image_bytes: bytes, content_type: str = 'image/jpeg') -> str:
    try:
        client = storage.Client()
        bucket = client.bucket(GCS_BUCKET_NAME)
        
        file_name = f"receipts/{uuid.uuid4()}.jpg"
        blob = bucket.blob(file_name)

        blob.upload_from_string(
            image_bytes,
            content_type=content_type
        )
        
        return blob.public_url

    except Exception as e:
        logger.error(f"Error uploading to GCS: {e}")
        return None