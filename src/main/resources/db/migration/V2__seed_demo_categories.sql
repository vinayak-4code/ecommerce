insert into categories(id, parent_id, name, slug, active, created_at)
values
('00000000-0000-0000-0000-000000000100', null, 'Electronics', 'electronics', true, now()),
('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000100', 'Mobile Phones', 'mobile-phones', true, now()),
('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000100', 'Laptops', 'laptops', true, now()),
('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000100', 'Cameras', 'cameras', true, now());

insert into category_attribute_definitions(id, category_id, name, code, attribute_type, required, searchable)
values
('00000000-0000-0000-0000-000000001001', '00000000-0000-0000-0000-000000000101', 'RAM', 'ram', 'STRING', true, true),
('00000000-0000-0000-0000-000000001002', '00000000-0000-0000-0000-000000000101', 'Storage', 'storage', 'STRING', true, true),
('00000000-0000-0000-0000-000000001003', '00000000-0000-0000-0000-000000000101', 'Screen Size', 'screen_size', 'STRING', false, true),
('00000000-0000-0000-0000-000000001004', '00000000-0000-0000-0000-000000000101', 'Battery Capacity', 'battery_capacity', 'STRING', false, true),
('00000000-0000-0000-0000-000000001005', '00000000-0000-0000-0000-000000000102', 'Processor', 'processor', 'STRING', true, true),
('00000000-0000-0000-0000-000000001006', '00000000-0000-0000-0000-000000000102', 'RAM', 'ram', 'STRING', true, true),
('00000000-0000-0000-0000-000000001007', '00000000-0000-0000-0000-000000000102', 'Storage', 'storage', 'STRING', true, true),
('00000000-0000-0000-0000-000000001008', '00000000-0000-0000-0000-000000000102', 'Display Size', 'display_size', 'STRING', false, true);
