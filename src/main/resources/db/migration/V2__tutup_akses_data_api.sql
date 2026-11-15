-- Kalau database-nya di Supabase, tabel di schema public bisa dijangkau lewat Data API (PostgREST)
-- dengan anon key. MiniBank tidak memakai Data API sama sekali: semua akses lewat aplikasi Java,
-- yang konek sebagai pemilik tabel dan tidak terkena RLS. Jadi RLS dinyalakan tanpa policy
-- (semua baris tertutup untuk role lain), dan hak role anon/authenticated di tabel MiniBank dicabut kalau role itu ada.
-- Di PostgreSQL biasa (lokal, Docker, test) migrasi ini tetap aman dijalankan.

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;

-- Catatan: flyway_schema_history sengaja tidak disentuh di sini. Tabel itu sedang dipegang Flyway
-- selama migrasi berjalan, jadi mengubahnya dari dalam migrasi membuat proses saling menunggu.
-- Isinya hanya daftar nama file migrasi, bukan data nasabah.
DO $$
DECLARE
    r text;
    t text;
BEGIN
    FOREACH r IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
            FOREACH t IN ARRAY ARRAY['users', 'accounts', 'transactions', 'ledger_entries', 'idempotency_keys', 'audit_logs'] LOOP
                EXECUTE format('REVOKE ALL ON TABLE public.%I FROM %I', t, r);
            END LOOP;
        END IF;
    END LOOP;
END
$$;
