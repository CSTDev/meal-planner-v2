"""
Recipe Scraper Service - Kafka Consumer/Producer
Consumes RecipeScrapeRequested events and publishes RecipeScrapeCompleted events
"""

import json
import logging
from datetime import datetime, timezone
from typing import Dict, List, Optional
from dataclasses import dataclass, asdict
from uuid import uuid4
from recipe_scrapers import scrape_me
import os

from kafka import KafkaConsumer, KafkaProducer
from kafka.errors import KafkaError

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


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
    ingredients: List[str]
    instructions: List[str]
    prep_time: Optional[int]
    cook_time: Optional[int]
    total_time: Optional[int]
    servings: Optional[str]
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
        consumer_group: str = 'scraper-service'
    ):
        self.topic_name = topic_name
        self.return_topic_name = return_topic_name

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

        Args:
            url: The URL of the recipe to scrape

        Returns:
            Dictionary containing scraped recipe data
        """
        logger.info(f"Scraping recipe from: {url}")

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
                    ingredients=scraped_data.get('ingredients', []),
                    instructions=scraped_data.get(
                        'instructions_list', []),
                    prep_time=None,  # Not in scraped data
                    cook_time=None,  # Not in scraped data
                    total_time=scraped_data.get('total_time'),
                    servings="2",  # scraped_data.get('yields'),
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
    # Configuration (could be loaded from environment variables)
    KAFKA_BOOTSTRAP_SERVERS = os.getenv(
        'KAFKA_BOOTSTRAP_SERVERS', 'kafka:29092')
    KAFKA_TOPIC = 'scrape-requests'
    RETURN_TOPIC = 'scraped-recipes'
    CONSUMER_GROUP = 'scraper-service'

    # Create and start the service
    service = RecipeScraperService(
        bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
        topic_name=KAFKA_TOPIC,
        return_topic_name=RETURN_TOPIC,
        consumer_group=CONSUMER_GROUP
    )

    service.start()


if __name__ == '__main__':
    main()
