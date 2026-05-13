-- Join table for service_providers <-> services many-to-many relationship
CREATE TABLE IF NOT EXISTS service_provider_services (
    provider_id UUID NOT NULL REFERENCES service_providers(id) ON DELETE CASCADE,
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    PRIMARY KEY (provider_id, service_id)
);
