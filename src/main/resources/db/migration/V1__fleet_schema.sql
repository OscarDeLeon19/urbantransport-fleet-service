CREATE TABLE branches_projection (
                                     id          UUID PRIMARY KEY,
                                     name        VARCHAR(120) NOT NULL,
                                     code        VARCHAR(20),
                                     active      BOOLEAN      NOT NULL DEFAULT TRUE,
                                     updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE buses (
                       id                UUID PRIMARY KEY,
                       plate             VARCHAR(15)   NOT NULL,
                       brand             VARCHAR(60)   NOT NULL,
                       model             VARCHAR(60)   NOT NULL,
                       manufacture_year  SMALLINT      NOT NULL,
                       capacity          INTEGER       NOT NULL,
                       photo_url         VARCHAR(500),
                       status            VARCHAR(20)   NOT NULL,
                       odometer_km       NUMERIC(12,2) NOT NULL DEFAULT 0,
                       branch_id         UUID          NOT NULL REFERENCES branches_projection (id),
                       created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
                       updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE drivers (
                         id                    UUID PRIMARY KEY,
                         user_id               UUID UNIQUE,
                         full_name             VARCHAR(150)  NOT NULL,
                         license_number        VARCHAR(30)   NOT NULL,
                         license_type          VARCHAR(2)    NOT NULL,
                         license_expires_on    DATE          NOT NULL,
                         phone                 VARCHAR(20)   NOT NULL,
                         email                 VARCHAR(150)  NOT NULL,
                         base_salary_per_trip  NUMERIC(10,2) NOT NULL,
                         photo_url             VARCHAR(500),
                         branch_id             UUID          NOT NULL REFERENCES branches_projection (id),
                         active                BOOLEAN       NOT NULL DEFAULT TRUE,
                         created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
                         updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE routes (
                        id                     UUID PRIMARY KEY,
                        origin_branch_id       UUID          NOT NULL REFERENCES branches_projection (id),
                        destination_branch_id  UUID          NOT NULL REFERENCES branches_projection (id),
                        distance_km            NUMERIC(8,2)  NOT NULL,
                        ticket_price           NUMERIC(10,2) NOT NULL,
                        created_by             UUID,
                        deleted                BOOLEAN       NOT NULL DEFAULT FALSE,
                        deleted_at             TIMESTAMPTZ,
                        created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
                        updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE license_alerts (
                                id           UUID PRIMARY KEY,
                                driver_id    UUID        NOT NULL REFERENCES drivers (id) ON DELETE CASCADE,
                                days_before  SMALLINT    NOT NULL,
                                expires_on   DATE        NOT NULL,
                                notify_on    DATE        NOT NULL,
                                sent_at      TIMESTAMPTZ
);

CREATE TABLE outbox_events (
                               id              UUID PRIMARY KEY,
                               aggregate_type  VARCHAR(50)  NOT NULL,
                               aggregate_id    UUID         NOT NULL,
                               event_type      VARCHAR(80)  NOT NULL,
                               topic           VARCHAR(80)  NOT NULL,
                               payload         JSONB        NOT NULL,
                               created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
                               published_at    TIMESTAMPTZ,
                               attempts        INTEGER      NOT NULL DEFAULT 0,
                               last_error      TEXT
);

CREATE TABLE processed_events (
                                  event_id      UUID PRIMARY KEY,
                                  event_type    VARCHAR(80) NOT NULL,
                                  processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);