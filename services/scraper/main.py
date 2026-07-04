"""
Recipe Scraper Service - Kafka Consumer/Producer
Consumes RecipeScrapeRequested events and publishes RecipeScrapeCompleted events
"""

import json
import logging
import os
import re
import time
from collections import defaultdict
from datetime import datetime, timezone
from typing import Dict, List, Optional
from dataclasses import dataclass
from urllib.parse import urlparse
from uuid import uuid4

from recipe_scrapers import scrape_me

from kafka import KafkaConsumer, KafkaProducer
from kafka.errors import KafkaError
from prometheus_client import Counter, Histogram, start_http_server

class RateLimiter:
    """
    Per-host rate limiter that enforces a minimum delay between requests
    to the same host.
    """

    def __init__(self, min_delay_seconds: float = 2.0):
        self.min_delay_seconds = min_delay_seconds
        self._last_request_time: Dict[str, float] = defaultdict(float)

    def wait(self, host: str) -> None:
        """Block until it is safe to make the next request to host."""
        now = time.monotonic()
        elapsed = now - self._last_request_time[host]
        remaining = self.min_delay_seconds - elapsed
        if remaining > 0:
            logger_rl = logging.getLogger(__name__)
            logger_rl.debug(f"Rate limiting: waiting {remaining:.2f}s before next request to {host}")
            time.sleep(remaining)
        self._last_request_time[host] = time.monotonic()


_UNITS = {
    'cups', 'cup', 'c',
    'tablespoons', 'tablespoon', 'tbsp', 'tbs',
    'teaspoons', 'teaspoon', 'tsp',
    'ounces', 'ounce', 'oz',
    'pounds', 'pound', 'lbs', 'lb',
    'grams', 'gram', 'g',
    'kilograms', 'kilogram', 'kg',
    'milliliters', 'milliliter', 'ml',
    'liters', 'liter', 'l',
    'pints', 'pint', 'pt',
    'quarts', 'quart', 'qt',
    'gallons', 'gallon',
    'bunches', 'bunch',
    'cloves', 'clove',
    'slices', 'slice',
    'pieces', 'piece', 'pcs',
    'cans', 'can',
    'packages', 'package', 'pkg',
    'sticks', 'stick',
    'pinches', 'pinch',
    'dashes', 'dash',
    'handfuls', 'handful',
    'sprigs', 'sprig',
    'heads', 'head',
    'inches', 'inch',
    'links', 'link',
}

_UNICODE_FRACTIONS = {
    '½': '1/2', '⅓': '1/3', '⅔': '2/3', '¼': '1/4', '¾': '3/4',
    '⅕': '1/5', '⅖': '2/5', '⅗': '3/5', '⅘': '4/5', '⅙': '1/6',
    '⅚': '5/6', '⅛': '1/8', '⅜': '3/8', '⅝': '5/8', '⅞': '7/8',
}


def _replace_unicode_fractions(s: str) -> str:
    for frac, replacement in _UNICODE_FRACTIONS.items():
        s = s.replace(frac, replacement)
    return s


def _parse_quantity(s: str) -> float:
    s = s.strip()
    parts = s.split()
    if len(parts) == 2 and '/' in parts[1]:
        whole = float(parts[0])
        num, den = parts[1].split('/')
        return whole + float(num) / float(den)
    if '/' in s:
        num, den = s.split('/')
        return float(num.strip()) / float(den.strip())
    return float(s)


# Words that can appear between the quantity and the unit and should be skipped
# e.g. "1 level tbsp" or "1 heaped tsp"
_UNIT_ADJECTIVES = {'level', 'heaped', 'heaping', 'flat', 'rounded', 'scant'}


def parse_ingredient(ingredient_str: str) -> Optional[dict]:
    """Parse an ingredient string into quantity, unit, and name.

    Returns None for zero-multiplier serving-variant lines (trailing ``x0``),
    which should be filtered out by the caller.

    Handles common edge cases including:
    - Unicode fractions (½, ¼ …)
    - Mixed numbers (1 1/2)
    - Units attached directly to numbers with no space (e.g. 250g, 30ml)
    - Adjectives before units (e.g. "1 level tbsp")
    - Prefix multiplier notation (e.g. "2 x 120g")
    - Trailing serving-size multiplier (e.g. "Chicken breast (300g) x2")
    - Embedded parenthetical weight/volume (e.g. "Diced chicken breast (250g)")
    - Leading "of" after a unit word (e.g. "handful of basil")
    """
    text = _replace_unicode_fractions(ingredient_str.strip())

    # --- Trailing xN serving-variant multiplier ---
    # Gousto (and some other sites) emit one line per serving-size option,
    # tagged with a trailing "xN" token.  x0 = unselected variant → drop.
    # xN (N > 0) = selected variant with a quantity multiplier.
    trailing_multiplier = 1
    trailing_x = re.search(r'\s*x(\d+)\s*$', text, re.IGNORECASE)
    if trailing_x:
        n = int(trailing_x.group(1))
        if n == 0:
            return None  # zero-multiplier line: discard entirely
        trailing_multiplier = n
        text = text[:trailing_x.start()].strip()

    # --- Embedded parenthetical weight/volume (e.g. "(250g)", "(2pcs)") ---
    # If the string carries a "(NUNITstring)" block and the unit is recognised,
    # extract quantity/unit from it and strip the block from the name.
    # Apply any trailing multiplier to the extracted quantity.
    paren_match = re.search(r'\((\d+(?:\.\d+)?)\s*([a-zA-Z]+)\)', text)
    if paren_match:
        paren_unit = paren_match.group(2)
        if paren_unit.lower() in _UNITS:
            name = (text[:paren_match.start()] + text[paren_match.end():]).strip()
            try:
                paren_qty = float(paren_match.group(1)) * trailing_multiplier
            except ValueError:
                paren_qty = 0.0
            return {'quantity': paren_qty, 'unit': paren_unit.lower(), 'name': name or ingredient_str}

    quantity = 0.0

    # Match quantity: integer, decimal, fraction, or mixed number (e.g. "1 1/2")
    # Also handles number+unit-attached directly (e.g. "250g") — captured as number only
    qty_match = re.match(r'^(\d+(?:\s+\d+/\d+|\.\d+|/\d+)?)', text)
    if qty_match:
        try:
            quantity = _parse_quantity(qty_match.group(1))
        except (ValueError, ZeroDivisionError):
            quantity = 0.0
        text = text[qty_match.end():].strip()

        # Handle "2 x 120g" prefix-multiplier notation: skip "x <number>" and treat
        # the first number as the count, keeping the rest for further parsing.
        # This is the pre-existing path; trailing_multiplier is 1 here because
        # "2 x 120g" has no trailing xN suffix.
        x_match = re.match(r'^x\s+(\d+(?:\.\d+)?)\s*', text, re.IGNORECASE)
        if x_match:
            text = text[x_match.end():].strip()

    # Handle number glued to unit (e.g. "250g", "30ml") — no space between them.
    # Only relevant when text still starts with digits after stripping the main number.
    # This case is already handled because qty_match only consumes digits/fraction chars;
    # if there was no space before the unit it would have been left in text.
    glued_match = re.match(r'^(\d+(?:\.\d+)?)\s*([a-zA-Z]+)(.*)', text)
    if glued_match:
        potential_unit = glued_match.group(2).lower()
        if potential_unit in _UNITS:
            try:
                extra_qty = float(glued_match.group(1))
                # Use this quantity only if we have no quantity yet (handles "250g" alone)
                if quantity == 0.0:
                    quantity = extra_qty
            except ValueError:
                pass
            text = glued_match.group(3).strip()
            # Return with this as the unit
            name = text[3:].strip() if text.lower().startswith('of ') else text
            return {'quantity': quantity * trailing_multiplier, 'unit': glued_match.group(2), 'name': name or ingredient_str}

    # Skip optional adjective before unit (e.g. "level", "heaped")
    words = text.split()
    if words and words[0].lower() in _UNIT_ADJECTIVES:
        words = words[1:]
        text = ' '.join(words)

    unit = ''
    words = text.split()
    if words and words[0].lower().rstrip('.') in _UNITS:
        unit = words[0]
        text = ' '.join(words[1:]).strip()

    # Strip a leading "of" that sometimes follows unit words (e.g. "handful of basil")
    if text.lower().startswith('of '):
        text = text[3:].strip()

    return {'quantity': quantity * trailing_multiplier, 'unit': unit, 'name': text or ingredient_str}


def parse_servings(yields_str) -> int:
    """Extract the serving count from a yields string.

    Handles formats like:
    - '4 servings'
    - 'Serves 4'
    - 'Makes 4 portions'
    - '4'
    - '2-3' (returns the first number)
    """
    if not yields_str:
        return 0
    match = re.search(r'\d+', str(yields_str))
    return int(match.group()) if match else 0


# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Prometheus metrics
SCRAPE_REQUESTS_TOTAL = Counter(
    'scraper_requests_total',
    'Total number of scrape requests received',
    ['status']  # labels: success, failure
)
SCRAPE_DURATION_SECONDS = Histogram(
    'scraper_duration_seconds',
    'Time spent scraping a recipe URL',
    buckets=[0.5, 1.0, 2.5, 5.0, 10.0, 30.0, 60.0]
)
EVENTS_PUBLISHED_TOTAL = Counter(
    'scraper_events_published_total',
    'Total Kafka events published by the scraper',
    ['event_type']  # labels: scrape-completed, scrape-failed
)


# Data classes matching Java Records
@dataclass
class EventMetadata:
    source_service: str
    correlation_id: str
    user_id: str


@dataclass
class RecipeData:
    title: str
    description: Optional[str]
    ingredients: List[dict]
    instructions: List[str]
    prep_time: Optional[int]
    cook_time: Optional[int]
    total_time: Optional[int]
    servings: int
    image_url: Optional[str]
    canonical_url: str
    host: str
    ratings: Optional[float]
    language: Optional[str]


@dataclass
class RecipeScrapeRequested:
    event_id: str
    timestamp: str
    metadata: EventMetadata
    url: str
    user_id: str


@dataclass
class RecipeScrapeCompleted:
    event_id: str
    timestamp: str
    metadata: EventMetadata
    user_id: str
    url: str
    recipe_data: RecipeData

    def to_dict(self):
        """Convert to dictionary for JSON serialization"""
        return {
            '@type': 'scrape-completed',
            'eventId': self.event_id,
            'timestamp': self.timestamp,
            'metadata': {
                'sourceService': self.metadata.source_service,
                'correlationId': self.metadata.correlation_id,
                'userId': self.metadata.user_id
            },
            'userId': self.user_id,
            'url': self.url,
            'recipeData': {
                'title': self.recipe_data.title,
                'description': self.recipe_data.description,
                'ingredients': self.recipe_data.ingredients,
                'instructions': self.recipe_data.instructions,
                'prepTime': self.recipe_data.prep_time,
                'cookTime': self.recipe_data.cook_time,
                'totalTime': self.recipe_data.total_time,
                'servings': self.recipe_data.servings,
                'imageUrl': self.recipe_data.image_url,
                'canonicalUrl': self.recipe_data.canonical_url,
                'host': self.recipe_data.host,
                'ratings': self.recipe_data.ratings,
                'language': self.recipe_data.language
            }
        }


@dataclass
class RecipeScrapeFailed:
    event_id: str
    timestamp: str
    metadata: EventMetadata
    url: str
    user_id: str
    error_message: str

    def to_dict(self):
        """Convert to dictionary for JSON serialization"""
        return {
            '@type': 'scrape-failed',
            'eventId': self.event_id,
            'timestamp': self.timestamp,
            'metadata': {
                'sourceService': self.metadata.source_service,
                'correlationId': self.metadata.correlation_id,
                'userId': self.metadata.user_id
            },
            'url': self.url,
            'userId': self.user_id,
            'errorMessage': self.error_message
        }


class RecipeScraperService:
    """
    Recipe Scraper Service that consumes scrape requests from Kafka
    and publishes scrape results back to Kafka
    """

    def __init__(
        self,
        bootstrap_servers: str = 'localhost:9092',
        topic_name: str = 'recipe-scraping',
        return_topic_name: str = 'recipes',
        consumer_group: str = 'scraper-service',
        rate_limit_delay: float = 2.0
    ):
        self.topic_name = topic_name
        self.return_topic_name = return_topic_name
        self.rate_limiter = RateLimiter(min_delay_seconds=rate_limit_delay)

        # Initialize Kafka consumer
        self.consumer = KafkaConsumer(
            topic_name,
            bootstrap_servers=bootstrap_servers,
            group_id=consumer_group,
            value_deserializer=lambda m: json.loads(m.decode('utf-8')),
            auto_offset_reset='latest',
            enable_auto_commit=True
        )

        # Initialize Kafka producer
        self.producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode('utf-8'),
            acks='all',
            retries=3
        )

        logger.info(
            f"Recipe Scraper Service initialized. Listening on topic: {topic_name}")

    def scrape_recipe(self, url: str) -> Dict:
        """
        Method for scraping recipe from URL

        Applies per-host rate limiting so the same site is not hit too
        quickly in succession.

        Args:
            url: The URL of the recipe to scrape

        Returns:
            Dictionary containing scraped recipe data
        """
        host = urlparse(url).netloc or url
        self.rate_limiter.wait(host)

        logger.info(f"Scraping recipe from: {url}")
        with SCRAPE_DURATION_SECONDS.time():
            scraper = scrape_me(url)
            return scraper.to_json()

    def process_scrape_request(self, event: Dict) -> None:
        """
        Process a RecipeScrapeRequested event

        Args:
            event: The Kafka event dictionary
        """
        try:
            # Check if this is a scrape-requested event
            event_type = event.get('@type') or event.get('eventType')
            if event_type != 'scrape-requested':
                logger.debug(f"Ignoring event type: {event_type}")
                return

            # Extract event data (handle both camelCase and snake_case)
            event_id = event.get('eventId') or event.get('event_id')
            url = event.get('url')
            user_id = event.get('userId') or event.get('user_id')
            metadata = event.get('metadata', {})

            logger.info(
                f"Processing scrape request for URL: {url} (user: {user_id})")

            # Create correlation ID from original event
            correlation_id = metadata.get('correlationId') or metadata.get(
                'correlation_id') or str(uuid4())

            try:
                # Scrape the recipe (stubbed for now)
                scraped_data = self.scrape_recipe(url)

                # Build RecipeData object
                recipe_data = RecipeData(
                    title=scraped_data.get('title', 'Unknown Recipe'),
                    description=scraped_data.get('description'),
                    ingredients=[p for p in (parse_ingredient(i) for i in scraped_data.get('ingredients', [])) if p is not None],
                    instructions=scraped_data.get('instructions_list', []),
                    prep_time=None,
                    cook_time=None,
                    total_time=scraped_data.get('total_time'),
                    servings=parse_servings(scraped_data.get('yields')),
                    image_url=scraped_data.get('image'),
                    canonical_url=scraped_data.get('canonical_url', url),
                    host=scraped_data.get('host', ''),
                    ratings=scraped_data.get('ratings'),
                    language=scraped_data.get('language', 'en')
                )

                # Create success event
                completed_event = RecipeScrapeCompleted(
                    event_id=str(uuid4()),
                    timestamp=datetime.now(timezone.utc).isoformat(),
                    metadata=EventMetadata(
                        source_service='scraper-service',
                        correlation_id=correlation_id,
                        user_id=user_id
                    ),
                    user_id=user_id,
                    url=url,
                    recipe_data=recipe_data
                )

                # Publish to Kafka
                self.publish_event(completed_event.to_dict())
                SCRAPE_REQUESTS_TOTAL.labels(status='success').inc()
                EVENTS_PUBLISHED_TOTAL.labels(event_type='scrape-completed').inc()
                logger.info(
                    f"Successfully published scrape completed event for: {url}")

            except Exception as scrape_error:
                logger.error(f"Failed to scrape recipe: {scrape_error}")

                # Create failure event
                failed_event = RecipeScrapeFailed(
                    event_id=str(uuid4()),
                    timestamp=datetime.now(timezone.utc).isoformat(),
                    metadata=EventMetadata(
                        source_service='scraper-service',
                        correlation_id=correlation_id,
                        user_id=user_id
                    ),
                    url=url,
                    user_id=user_id,
                    error_message=str(scrape_error)
                )

                # Publish failure to Kafka
                self.publish_event(failed_event.to_dict())
                SCRAPE_REQUESTS_TOTAL.labels(status='failure').inc()
                EVENTS_PUBLISHED_TOTAL.labels(event_type='scrape-failed').inc()
                logger.info(f"Published scrape failed event for: {url}")

        except Exception as e:
            logger.error(
                f"Error processing scrape request: {e}", exc_info=True)

    def publish_event(self, event: Dict) -> None:
        """
        Publish an event to Kafka

        Args:
            event: The event dictionary to publish
        """
        try:
            future = self.producer.send(self.return_topic_name, value=event)
            # Block for synchronous send (optional, for reliability)
            record_metadata = future.get(timeout=10)
            logger.debug(
                f"Event published to topic {record_metadata.topic} "
                f"partition {record_metadata.partition} "
                f"offset {record_metadata.offset}"
            )
        except KafkaError as e:
            logger.error(f"Failed to publish event to Kafka: {e}")
            raise

    def start(self) -> None:
        """
        Start consuming messages from Kafka
        """
        logger.info("Starting Recipe Scraper Service...")

        try:
            for message in self.consumer:
                event = message.value
                logger.debug(f"Received message: {event}")
                self.process_scrape_request(event)

        except KeyboardInterrupt:
            logger.info("Shutting down Recipe Scraper Service...")
        finally:
            self.close()

    def close(self) -> None:
        """
        Clean up resources
        """
        logger.info("Closing Kafka connections...")
        self.consumer.close()
        self.producer.close()


def main():
    """
    Entry point for the service
    """
    # Configuration (loaded from environment variables)
    KAFKA_BOOTSTRAP_SERVERS = os.getenv(
        'KAFKA_BOOTSTRAP_SERVERS', 'kafka:29092')
    KAFKA_TOPIC = 'scrape-requests'
    RETURN_TOPIC = 'scraped-recipes'
    CONSUMER_GROUP = 'scraper-service'
    RATE_LIMIT_DELAY = float(os.getenv('SCRAPER_RATE_LIMIT_SECONDS', '2.0'))
    METRICS_PORT = int(os.getenv('SCRAPER_METRICS_PORT', '8000'))

    # Start Prometheus metrics HTTP server on a background thread
    start_http_server(METRICS_PORT)
    logger.info(f"Prometheus metrics available at http://0.0.0.0:{METRICS_PORT}/metrics")

    # Create and start the service
    service = RecipeScraperService(
        bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
        topic_name=KAFKA_TOPIC,
        return_topic_name=RETURN_TOPIC,
        consumer_group=CONSUMER_GROUP,
        rate_limit_delay=RATE_LIMIT_DELAY
    )

    service.start()


if __name__ == '__main__':
    main()
