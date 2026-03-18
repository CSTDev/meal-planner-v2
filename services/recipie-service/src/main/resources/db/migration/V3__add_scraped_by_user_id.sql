ALTER TABLE recipes ADD COLUMN scraped_by_user_id UUID;
CREATE INDEX idx_recipes_scraped_by_user ON recipes(scraped_by_user_id);
