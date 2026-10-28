-- Descripción libre de productos y personalizaciones (por ejemplo, la URL del producto en la web del fabricante)
ALTER TABLE products ADD COLUMN description VARCHAR(1000);
ALTER TABLE customizations ADD COLUMN description VARCHAR(1000);
