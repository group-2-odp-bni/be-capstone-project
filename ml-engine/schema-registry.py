SWAGGER_REGISTRY = {
    "/api/v1/split-bill/extract-text": {
        "post": {
            "summary": "Ekstraksi teks struk (OCR)",
            "description": "Mengunggah gambar struk dan/atau teks tambahan, diproses oleh Gemini.",
            "requestBody": {
                "required": True,
                "content": {
                    "multipart/form-data": {
                        "schema": {
                            "type": "object",
                            "properties": {
                                "receipt_image": {
                                    "type": "string",
                                    "format": "binary",
                                    "description": "File gambar struk (opsional jika extra_text diisi)."
                                },
                                "extra_text": {
                                    "type": "string",
                                    "description": "Teks tambahan (opsional)."
                                }
                            }
                        }
                    }
                }
            },
            "responses": {
                "201": {
                    "description": "Ekstraksi berhasil.",
                    "content": {
                        "application/json": {
                            "schema": {
                                "type": "object",
                                "properties": {
                                    "error": {"type": "boolean"},
                                    "message": {"type": "string"},
                                    "data": {
                                        "type": "object",
                                        "properties": {
                                            "ocr_id": {"type": "string"},
                                            "items_for_assignment": {"type": "array"},
                                            "components": {"type": "object"},
                                            "receipt_url": {"type": "string", "nullable": True}
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                "400": {"description": "Input tidak valid."},
                "502": {"description": "Gagal memanggil layanan AI."}
            }
        }
    },

    # Contoh lain: /api/v1/split-bill/bills
    "/api/v1/split-bill/bills": {
        "post": {
            "summary": "Buat split bill baru",
            "description": "Membuat dokumen split bill dengan items, fees, dan assignments.",
            "requestBody": {
                "required": True,
                "content": {
                    "application/json": {
                        "schema": {
                            "type": "object",
                            "properties": {
                                "title": {"type": "string"},
                                "destinationWalletId": {"type": "string"},
                                "items": {"type": "array"},
                                "fees": {"type": "object"},
                                "assignments": {"type": "array"},
                                "imageUrl": {"type": "string", "nullable": True},
                                "tax_strategy": {"type": "string"}
                            },
                            "required": ["destinationWalletId", "items", "assignments"]
                        }
                    }
                }
            },
            "responses": {
                "201": {
                    "description": "Bill dibuat.",
                    "content": {
                        "application/json": {
                            "schema": {
                                "type": "object",
                                "properties": {
                                    "error": {"type": "boolean"},
                                    "message": {"type": "string"},
                                    "data": {
                                        "type": "object",
                                        "properties": {
                                            "billId": {"type": "string"},
                                            "status": {"type": "string"},
                                            "ownerShortLink": {"type": "string"},
                                            "memberLinks": {"type": "array"}
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                "400": {"description": "Validasi gagal."}
            }
        }
    },
}
