-- خريطة المنحة: الشهادات/الأوراق المطلوبة والمتطلبات، كل عنصر بسطر
alter table opportunities add column required_documents text null;
alter table opportunities add column requirements text null;
