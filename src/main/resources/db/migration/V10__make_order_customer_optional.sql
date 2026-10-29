-- El cliente es opcional: un pedido puede ser para uno mismo
ALTER TABLE sales_orders ALTER COLUMN customer_id DROP NOT NULL;
