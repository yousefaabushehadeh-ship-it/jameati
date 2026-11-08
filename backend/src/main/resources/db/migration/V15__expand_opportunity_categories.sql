update site_settings
set value_json = concat(
      substring(value_json, 1, char_length(value_json) - 2),
      ',{"id":"awards","title":"مسابقات","subtitle":"جوائز ومسابقات وتحديات دولية","values":["awards","الفرص","7"],"active":true,"updatedAt":"2026-09-01T00:00:00Z"}]}'
    ),
    updated_at = current_timestamp(6)
where setting_key = 'categories'
  and value_json not like '%"id":"awards"%';

update site_settings
set value_json = concat(
      substring(value_json, 1, char_length(value_json) - 2),
      ',{"id":"financial-grants","title":"منح مالية","subtitle":"تمويل للمشروعات والمؤسسات والباحثين","values":["financial-grants","الفرص","8"],"active":true,"updatedAt":"2026-09-01T00:00:00Z"}]}'
    ),
    updated_at = current_timestamp(6)
where setting_key = 'categories'
  and value_json not like '%"id":"financial-grants"%';

update site_settings
set value_json = concat(
      substring(value_json, 1, char_length(value_json) - 2),
      ',{"id":"workshops","title":"ورشات","subtitle":"ورشات عمل ودورات حضورية وعبر الإنترنت","values":["workshops","الفرص والدورات","9"],"active":true,"updatedAt":"2026-09-01T00:00:00Z"}]}'
    ),
    updated_at = current_timestamp(6)
where setting_key = 'categories'
  and value_json not like '%"id":"workshops"%';

update site_settings
set value_json = concat(
      substring(value_json, 1, char_length(value_json) - 2),
      ',{"id":"university-programs","title":"برامج جامعية","subtitle":"برامج جامعية ومهنية ومسارات قبول","values":["university-programs","الفرص والدورات","10"],"active":true,"updatedAt":"2026-09-01T00:00:00Z"}]}'
    ),
    updated_at = current_timestamp(6)
where setting_key = 'categories'
  and value_json not like '%"id":"university-programs"%';
