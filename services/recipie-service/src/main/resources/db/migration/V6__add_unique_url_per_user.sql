-- Nothing has prevented duplicate (url, scraped_by_user_id) pairs until now,
-- so pre-existing recipes may already violate the constraint added below.
-- Postgres refuses to add a unique constraint over data that violates it, so
-- clean up existing duplicates first: for each (url, scraped_by_user_id)
-- group with more than one row, keep the earliest (by createdat, then id as
-- a deterministic tiebreaker) and delete the rest.
--
-- Rows with a null url are intentionally excluded — they are allowed to
-- repeat and are out of scope. Rows with a null scraped_by_user_id are also
-- excluded: standard SQL unique-constraint semantics never treat two nulls
-- as equal, so such rows could never violate the constraint being added and
-- must not be touched.
DELETE FROM recipes
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY url, scraped_by_user_id
                   ORDER BY createdat ASC, id ASC
               ) AS row_num
        FROM recipes
        WHERE url IS NOT NULL AND scraped_by_user_id IS NOT NULL
    ) ranked
    WHERE ranked.row_num > 1
);

-- Prevents the same user from scraping the same URL twice at the database
-- level: a safety net for the race condition where two identical scrape
-- requests are persisted concurrently, slipping past the application-level
-- pre-check in ScrapeResource. Standard SQL null semantics mean multiple
-- rows with a null url are still allowed, so pre-existing recipes without a
-- url are unaffected.
ALTER TABLE recipes ADD CONSTRAINT recipes_url_user_unique UNIQUE (url, scraped_by_user_id);
