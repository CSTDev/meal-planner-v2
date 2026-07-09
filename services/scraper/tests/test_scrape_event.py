"""
Tests for RecipeScrapeCompleted.to_dict() key naming and the
compute_prep_and_cook_time() helper used to build RecipeData from
scraped_data dicts.
"""
import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from main import (
    RecipeScrapeCompleted,
    RecipeData,
    EventMetadata,
    compute_prep_and_cook_time,
)


def _make_recipe_data(**overrides):
    defaults = dict(
        title='Test Recipe',
        description='A test recipe',
        ingredients=[],
        instructions=[],
        prep_time=10,
        cook_time=20,
        total_time=30,
        servings=4,
        image_url='https://example.com/image.jpg',
        canonical_url='https://example.com/recipe',
        host='example.com',
        ratings=4.5,
        language='en',
    )
    defaults.update(overrides)
    return RecipeData(**defaults)


def _make_event(recipe_data):
    return RecipeScrapeCompleted(
        event_id='evt-1',
        timestamp='2026-07-09T00:00:00Z',
        metadata=EventMetadata(
            source_service='scraper', correlation_id='corr-1', user_id='user-1'
        ),
        user_id='user-1',
        url='https://example.com/recipe',
        recipe_data=recipe_data,
    )


class TestToDictKeyNames:
    def test_uses_java_field_names(self):
        event = _make_event(_make_recipe_data())
        recipe_data_dict = event.to_dict()['recipeData']

        assert recipe_data_dict['prepTimeMinutes'] == 10
        assert recipe_data_dict['cookTimeMinutes'] == 20
        assert recipe_data_dict['url'] == 'https://example.com/recipe'

    def test_does_not_emit_old_key_names(self):
        event = _make_event(_make_recipe_data())
        recipe_data_dict = event.to_dict()['recipeData']

        assert 'prepTime' not in recipe_data_dict
        assert 'cookTime' not in recipe_data_dict
        assert 'canonicalUrl' not in recipe_data_dict


class TestComputePrepAndCookTime:
    def test_reads_prep_and_cook_time_from_scraped_data(self):
        scraped_data = {'prep_time': 10, 'cook_time': 20}

        prep_time, cook_time = compute_prep_and_cook_time(scraped_data)

        assert prep_time == 10
        assert cook_time == 20

    def test_none_times_handled_gracefully_no_keys(self):
        scraped_data = {}

        prep_time, cook_time = compute_prep_and_cook_time(scraped_data)

        assert prep_time is None
        assert cook_time is None

    def test_none_times_handled_gracefully_explicit_none(self):
        scraped_data = {'prep_time': None, 'cook_time': None, 'total_time': None}

        prep_time, cook_time = compute_prep_and_cook_time(scraped_data)

        assert prep_time is None
        assert cook_time is None

    def test_none_times_to_dict_emits_null(self):
        recipe_data = _make_recipe_data(prep_time=None, cook_time=None)
        event = _make_event(recipe_data)

        recipe_data_dict = event.to_dict()['recipeData']

        assert recipe_data_dict['prepTimeMinutes'] is None
        assert recipe_data_dict['cookTimeMinutes'] is None

    def test_fallback_uses_total_time_as_cook_time(self):
        scraped_data = {'total_time': 45}

        prep_time, cook_time = compute_prep_and_cook_time(scraped_data)

        assert prep_time is None
        assert cook_time == 45

    def test_fallback_does_not_fire_when_cook_time_present(self):
        scraped_data = {'cook_time': 20, 'total_time': 45}

        prep_time, cook_time = compute_prep_and_cook_time(scraped_data)

        assert prep_time is None
        assert cook_time == 20

    def test_fallback_does_not_fire_when_prep_time_present(self):
        scraped_data = {'prep_time': 15, 'total_time': 45}

        prep_time, cook_time = compute_prep_and_cook_time(scraped_data)

        assert prep_time == 15
        assert cook_time is None
