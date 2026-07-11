-- Extends the category attribute model used by the seller product form.
alter table category_attribute_definitions add column if not exists label_text varchar(160);
alter table category_attribute_definitions add column if not exists visible_to_customer boolean;
alter table category_attribute_definitions add column if not exists min_length integer;
alter table category_attribute_definitions add column if not exists max_length integer;
alter table category_attribute_definitions add column if not exists min_value numeric(19,4);
alter table category_attribute_definitions add column if not exists max_value numeric(19,4);
alter table category_attribute_definitions add column if not exists allowed_values varchar(1000);

update category_attribute_definitions
set label_text = coalesce(label_text, name),
    visible_to_customer = coalesce(visible_to_customer, true);

alter table category_attribute_definitions alter column label_text set not null;
alter table category_attribute_definitions alter column visible_to_customer set not null;

-- JWTs are longer than the earlier opaque bearer token, but we still persist them for logout/revocation.
alter table auth_tokens alter column token type varchar(2048);

-- Add friendly constraints to seed attributes for dynamic form rendering.
update category_attribute_definitions set allowed_values = '4GB,6GB,8GB,12GB,16GB,32GB,64GB' where code = 'ram';
update category_attribute_definitions set allowed_values = '64GB,128GB,256GB,512GB,1TB,512GB SSD,1TB SSD' where code = 'storage';
update category_attribute_definitions set max_length = 80 where attribute_type = 'STRING' and max_length is null;
