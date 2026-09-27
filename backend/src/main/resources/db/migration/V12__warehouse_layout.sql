CREATE TABLE warehouse_layout (
    warehouse_id BIGINT NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL,
    layout_json MEDIUMTEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_layout_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(id)
);
