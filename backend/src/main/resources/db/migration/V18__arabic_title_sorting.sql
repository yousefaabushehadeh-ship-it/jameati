alter table opportunities add column arabic_title boolean not null default false;
alter table catalog_items add column arabic_title boolean not null default false;

-- Existing imported Arabic records start with Arabic text after surrounding whitespace.
-- Future inserts and updates use the exact Unicode-script classifier in the entity callbacks.
update opportunities
set arabic_title = true
where trim(title) regexp '^[ء-ي]';

update catalog_items
set arabic_title = true
where trim(title) regexp '^[ء-ي]';

create index idx_opportunity_public_arabic_latest
  on opportunities (published, deleted, arabic_title, created_at, id);

create index idx_catalog_public_arabic_latest
  on catalog_items (type, published, deleted, arabic_title, created_at, id);
