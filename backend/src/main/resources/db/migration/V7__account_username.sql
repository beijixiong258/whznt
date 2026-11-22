ALTER TABLE account ADD COLUMN username varchar(20) NOT NULL DEFAULT '';

UPDATE account SET username = '管理员北极熊' WHERE role = 'ADMIN';
