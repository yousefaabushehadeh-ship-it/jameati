create index idx_catalog_public_latest
  on catalog_items (type, published, deleted, created_at);
