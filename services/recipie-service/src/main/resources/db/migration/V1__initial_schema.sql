create table recipe_ingredients (quantity float4, recipe_id uuid not null, name varchar(255), unit varchar(255));
create table recipes (cookTimeMinutes integer not null, prepTimeMinutes integer not null, servings integer not null, createdAt timestamp(6), scrapedAt timestamp(6), id uuid not null, description varchar(255), imageUrl varchar(255), title varchar(255), url varchar(255), instructions TEXT array, tags varchar(255) array, primary key (id));

