-- MiniBank: skema awal
-- Saldo rekening disimpan sebagai cache, sumber kebenarannya ada di ledger_entries.
-- Tiap transaksi wajib seimbang: total debit = total kredit.

CREATE TABLE users (
    id                   UUID PRIMARY KEY,
    full_name            VARCHAR(100) NOT NULL,
    email                VARCHAR(120) NOT NULL UNIQUE,
    phone                VARCHAR(20)  NOT NULL,
    password_hash        VARCHAR(100) NOT NULL,
    pin_hash             VARCHAR(100),
    pin_failed_attempts  INT          NOT NULL DEFAULT 0,
    pin_locked_until     TIMESTAMPTZ,
    role                 VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'ADMIN')),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    account_number  VARCHAR(10)   NOT NULL UNIQUE,
    owner_id        UUID REFERENCES users (id),
    type            VARCHAR(20)   NOT NULL CHECK (type IN ('SAVINGS', 'INTERNAL')),
    currency        VARCHAR(3)    NOT NULL DEFAULT 'IDR',
    status          VARCHAR(20)   NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    balance         NUMERIC(19, 2) NOT NULL DEFAULT 0,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- rekening nasabah tidak boleh minus; rekening internal (kas bank) boleh
    CONSTRAINT balance_not_negative CHECK (type = 'INTERNAL' OR balance >= 0),
    CONSTRAINT owner_required CHECK (type = 'INTERNAL' OR owner_id IS NOT NULL)
);
CREATE INDEX idx_accounts_owner ON accounts (owner_id);

CREATE TABLE transactions (
    id               UUID PRIMARY KEY,
    reference        VARCHAR(24)    NOT NULL UNIQUE,
    type             VARCHAR(20)    NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    status           VARCHAR(20)    NOT NULL CHECK (status IN ('COMPLETED')),
    amount           NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    description      VARCHAR(140),
    source_account_id UUID REFERENCES accounts (id),
    dest_account_id   UUID REFERENCES accounts (id),
    initiated_by     UUID           NOT NULL REFERENCES users (id),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX idx_transactions_source_created ON transactions (source_account_id, created_at);

CREATE TABLE ledger_entries (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  UUID           NOT NULL REFERENCES transactions (id),
    account_id      UUID           NOT NULL REFERENCES accounts (id),
    direction       VARCHAR(6)     NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount          NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    balance_after   NUMERIC(19, 2) NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_account_id ON ledger_entries (account_id, id);
CREATE INDEX idx_ledger_transaction ON ledger_entries (transaction_id);

CREATE TABLE idempotency_keys (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users (id),
    idem_key        VARCHAR(80)  NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    -- dicek saat commit, supaya kunci bisa diklaim duluan sebelum baris transaksinya ditulis
    transaction_id  UUID         NOT NULL REFERENCES transactions (id) DEFERRABLE INITIALLY DEFERRED,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_idem_user_key UNIQUE (user_id, idem_key)
);

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    UUID,
    action      VARCHAR(40)  NOT NULL,
    target      VARCHAR(80),
    ip_address  VARCHAR(45),
    details     VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_logs (created_at DESC);

-- Rekening kas internal bank: lawan pembukuan untuk setor dan tarik tunai.
INSERT INTO accounts (id, account_number, owner_id, type, currency, status, balance)
VALUES ('00000000-0000-0000-0000-000000000001', '9000000001', NULL, 'INTERNAL', 'IDR', 'ACTIVE', 0);
