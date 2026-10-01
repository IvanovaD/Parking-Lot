CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE parking_space (
    id BIGSERIAL PRIMARY KEY,
    space_number INTEGER NOT NULL UNIQUE CHECK (space_number BETWEEN 1 AND 100)
);

CREATE TABLE reservation (
    id UUID PRIMARY KEY,
    parking_space_id BIGINT NOT NULL REFERENCES parking_space(id),
    license_plate VARCHAR(20) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (end_time = start_time + INTERVAL '1 hour')
);

ALTER TABLE reservation
    ADD CONSTRAINT reservation_no_active_space_overlap
    EXCLUDE USING gist (
        parking_space_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    ) WHERE (status = 'ACTIVE');

CREATE INDEX idx_reservation_active_interval
    ON reservation (start_time, end_time)
    WHERE status = 'ACTIVE';

CREATE TABLE idempotency_record (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    reservation_id UUID NOT NULL UNIQUE REFERENCES reservation(id),
    created_at TIMESTAMPTZ NOT NULL
);

INSERT INTO parking_space (space_number)
SELECT generate_series(1, 100);
