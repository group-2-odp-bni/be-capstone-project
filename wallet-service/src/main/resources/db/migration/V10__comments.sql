COMMENT ON TABLE wallet_oltp.wallets IS
'Master dompet. Saldo dimirror dari transaction-service via event (tidak diubah langsung).';
COMMENT ON COLUMN wallet_oltp.wallets.type IS 'PERSONAL | SHARED | FAMILY';
COMMENT ON TABLE wallet_oltp.wallet_members IS
'Relasi user<->wallet; unique partial index menjamin satu OWNER aktif/diundang per wallet.';
COMMENT ON TABLE wallet_oltp.wallet_user_limits IS
'Konfigurasi limit per user (per-tx/daily/weekly/monthly); enforcement di transaction-service.';
COMMENT ON TABLE wallet_oltp.wallet_type_policy IS
'Aturan default per tipe dompet (max_members, default caps, role debit).';
COMMENT ON TABLE wallet_oltp.wallet_aliases IS
'Alias publik/semi publik untuk menerima dana ke wallet tertentu.';
COMMENT ON TABLE wallet_read.wallets IS
'Read-model untuk inquiry cepat;';
COMMENT ON TABLE wallet_oltp.user_counters IS
'Counter lifetime created wallets per user; diincrement saat insert wallets.';
