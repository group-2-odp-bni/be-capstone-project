PROMPT_TEXT = """
Analisis gambar struk pembayaran ini secara mendetail. Ekstrak informasi berikut dan berikan HANYA dalam format JSON yang valid tanpa markdown (```json ... ```).

Pastikan format JSON memiliki struktur seperti ini:
{
  "items": [
    {
      "nama_item": "Nama Makanan/Minuman",
      "kuantitas": 1,
      "harga_total": 50000
    }
  ],
  "subtotal": 150000,
  "pajak": 15000,
  "biaya_layanan": 7500,
  "total_akhir": 172500,
  "metode_pembayaran": "QRIS",
  "detail_pembayaran": {
    "jenis_wallet": "GoPay",
    "nomor_rekening_atau_id": null
  }
}

Aturan Ekstraksi:
1.  **items**: Harus berupa array dari objek. Setiap objek MESTI memiliki 'nama_item' (string), 'kuantitas' (integer), dan 'harga_total' (integer, harga setelah dikali kuantitas). Jangan sertakan harga satuan.
2.  **subtotal**: Total harga semua item sebelum pajak dan biaya layanan. Jika tidak ada, isi dengan 0.
3.  **pajak**: Jumlah pajak (misal: PPN, Tax). Jika tidak ada, isi dengan 0.
4.  **biaya_layanan**: Jumlah biaya servis (service charge). Jika tidak ada, isi dengan 0.
5.  **total_akhir**: Total tagihan final yang harus dibayar.
6.  **metode_pembayaran**: Identifikasi metode pembayaran seperti 'QRIS', 'TUNAI', 'DEBIT', 'KARTU KREDIT'. Jika tidak terdeteksi, isi dengan null.
7.  **detail_pembayaran**:
    - **jenis_wallet**: Jika pembayaran QRIS atau e-wallet, coba identifikasi namanya (misal: 'GoPay', 'OVO', 'Dana'). Jika tidak ada, isi null.
    - **nomor_rekening_atau_id**: Jika ada nomor rekening atau ID transaksi yang relevan, masukkan di sini. Jika tidak ada, isi null.

Jika ada informasi yang tidak ditemukan pada struk, isi nilainya dengan null untuk string, dan 0 untuk angka. Jika ada mata uang yang bukan rupiah silahkan konversi mengikuti nilai tukar terakhir ke rupiah.
"""