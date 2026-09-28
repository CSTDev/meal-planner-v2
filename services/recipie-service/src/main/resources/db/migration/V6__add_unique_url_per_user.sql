-- Prevents the same user from scraping the same URL twice at the database
-- level: a safety net for the race condition where two identical scrape
-- requests are persisted concurrently, slipping past the application-level
-- pre-check in ScrapeResource. Standard SQL null semantics mean multiple
-- rows with a null url are still allowed, so pre-existing recipes without a
-- url are unaffected.
ALTER TABLE recipes ADD CONSTRAINT recipes_url_user_unique UNIQUE (url, scraped_by_user_id);
