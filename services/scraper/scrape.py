from recipe_scrapers import scrape_me

scraper = scrape_me(
    "https://www.gousto.co.uk/cookbook/recipes/tteokbokki-style-gnocchi-with-sesame-broccoli")
scraper.title()
scraper.instructions()
print(scraper.to_json())
