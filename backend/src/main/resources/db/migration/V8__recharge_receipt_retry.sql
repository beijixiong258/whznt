ALTER TABLE recharge_order DROP CONSTRAINT recharge_order_owner_id_receipt_sha256_key;

CREATE UNIQUE INDEX recharge_order_active_receipt_idx ON recharge_order (owner_id, receipt_sha256)
    WHERE state IN ('PENDING', 'CREDITED');
