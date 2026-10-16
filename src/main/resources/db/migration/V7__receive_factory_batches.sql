ALTER TABLE factory_batches ADD COLUMN received_at TIMESTAMP;

-- Camisetas que no llegaron en un lote. Dejan de pertenecer al lote (así se pueden pedir en otro),
-- pero queda constancia de que faltaron en este. Una misma camiseta puede faltar en varios lotes.
CREATE TABLE factory_batch_missing_lines (
    factory_batch_id    BIGINT NOT NULL,
    sales_order_line_id BIGINT NOT NULL,
    CONSTRAINT pk_factory_batch_missing_lines PRIMARY KEY (factory_batch_id, sales_order_line_id),
    CONSTRAINT fk_factory_batch_missing_lines_batch FOREIGN KEY (factory_batch_id) REFERENCES factory_batches (id),
    CONSTRAINT fk_factory_batch_missing_lines_line FOREIGN KEY (sales_order_line_id) REFERENCES sales_order_lines (id)
);
