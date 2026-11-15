-- Kalau database-nya di Supabase, tabel di schema public bisa dijangkau lewat Data API (PostgREST)
-- dengan anon key. MiniBank tidak memakai Data API sama sekali: semua akses lewat aplikasi Java,
-- yang konek sebagai pemilik tabel dan tidak terkena RLS. Jadi RLS dinyalakan tanpa policy
-- (semua baris tertutup untuk role lain), dan hak role anon/authenticated dicabut kalau role itu ada.
-- Di PostgreSQL biasa (lokal, Docker, test) migrasi ini tetap aman dijalankan.

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    r text;
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        EXECUTE 'ALTER TABLE public.flyway_schema_history ENABLE ROW LEVEL SECURITY';
    END IF;
    FOREACH r IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
            EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I', r);
            EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', r);
        END IF;
    END LOOP;
END
$$;
