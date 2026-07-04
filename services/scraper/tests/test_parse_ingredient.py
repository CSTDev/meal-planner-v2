"""
Regression tests for parse_ingredient, covering the Gousto serving-variant
fixture table (11 raw lines → 7 clean ingredients).
"""
import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

import pytest
from main import parse_ingredient


# ---------------------------------------------------------------------------
# Fixtures: (raw_string, expected_or_None)
# None means the line should be dropped (x0 multiplier).
# ---------------------------------------------------------------------------

CHOW_MEIN_FIXTURE = [
    (
        'Medium egg noodle nests (2pcs)',
        {'name': 'Medium egg noodle nests', 'quantity': 2.0, 'unit': 'pcs'},
    ),
    ('Diced chicken breast (125g) x0', None),
    (
        'Diced chicken breast (250g)',
        {'name': 'Diced chicken breast', 'quantity': 250.0, 'unit': 'g'},
    ),
    ('Toasted sesame oil (20ml) x0', None),
    (
        'Toasted sesame oil (10ml)',
        {'name': 'Toasted sesame oil', 'quantity': 10.0, 'unit': 'ml'},
    ),
    (
        'Hoisin sauce (40g)',
        {'name': 'Hoisin sauce', 'quantity': 40.0, 'unit': 'g'},
    ),
    ('Soy sauce (15ml) x0', None),
    (
        'Soy sauce (30ml)',
        {'name': 'Soy sauce', 'quantity': 30.0, 'unit': 'ml'},
    ),
    (
        'Sweet pointed pepper',
        {'name': 'Sweet pointed pepper', 'quantity': 0.0, 'unit': ''},
    ),
    ('Hoisin sauce (20g) x0', None),
    (
        'Sweet pea pods (80g)',
        {'name': 'Sweet pea pods', 'quantity': 80.0, 'unit': 'g'},
    ),
]


class TestParseIngredientGoustoFixture:
    """Verify every row in the Chow Mein regression fixture."""

    @pytest.mark.parametrize('raw,expected', CHOW_MEIN_FIXTURE)
    def test_fixture_row(self, raw, expected):
        result = parse_ingredient(raw)
        assert result == expected

    def test_full_fixture_produces_7_ingredients(self):
        """11 raw lines collapse to exactly 7 clean, non-None results."""
        results = [parse_ingredient(raw) for raw, _ in CHOW_MEIN_FIXTURE]
        kept = [r for r in results if r is not None]
        assert len(kept) == 7

    def test_zero_multiplier_lines_are_dropped(self):
        """Lines ending in x0 return None."""
        assert parse_ingredient('Diced chicken breast (125g) x0') is None
        assert parse_ingredient('Toasted sesame oil (20ml) x0') is None
        assert parse_ingredient('Soy sauce (15ml) x0') is None
        assert parse_ingredient('Hoisin sauce (20g) x0') is None


class TestNonzeroTrailingMultiplier:
    """Nonzero xN suffix multiplies the extracted parenthetical quantity."""

    def test_x2_doubles_quantity(self):
        result = parse_ingredient('Chicken breast (300g) x2')
        assert result is not None
        assert result['quantity'] == pytest.approx(600.0)
        assert result['unit'] == 'g'
        assert result['name'] == 'Chicken breast'

    def test_x3_triples_quantity(self):
        result = parse_ingredient('Olive oil (10ml) x3')
        assert result is not None
        assert result['quantity'] == pytest.approx(30.0)
        assert result['unit'] == 'ml'
        assert result['name'] == 'Olive oil'


class TestTrailingMultiplierNoSpace:
    """Trailing xN without a preceding space is handled correctly."""

    def test_no_space_before_multiplier_applies(self):
        """'Ingredient(250g)x2' — no space before x — multiplier must still be applied."""
        result = parse_ingredient('Ingredient(250g)x2')
        assert result is not None
        assert result['quantity'] == pytest.approx(500.0)
        assert result['unit'] == 'g'
        assert 'x2' not in result['name']

    def test_no_space_zero_multiplier_drops_line(self):
        """'(250g)x0' — no space before x — line must still be dropped."""
        result = parse_ingredient('Ingredient (250g)x0')
        assert result is None

    def test_word_embedded_xN_not_treated_as_multiplier(self):
        """'Chicken mix2' — x followed by digit inside a word must NOT be a multiplier.

        With the word-boundary guard ((?<!\\w)), the x in 'mix2' is preceded by
        a word character ('i'), so the trailing-multiplier regex must not match.
        The name should be returned unchanged.
        """
        result = parse_ingredient('Chicken mix2')
        assert result is not None
        assert result['name'] == 'Chicken mix2'
        assert result['quantity'] == pytest.approx(0.0)


class TestParenthesisUnitLowercased:
    """Parenthetical unit is returned lowercase regardless of input case."""

    def test_uppercase_unit_returned_lowercase(self):
        result = parse_ingredient('Chicken (300G)')
        assert result is not None
        assert result['unit'] == 'g'

    def test_mixed_case_unit_returned_lowercase(self):
        result = parse_ingredient('Broth (500Ml)')
        assert result is not None
        assert result['unit'] == 'ml'


class TestExistingPrefixMultiplierUnchanged:
    """The existing '2 x 120g' prefix-multiplier behaviour must not regress."""

    def test_prefix_multiplier_parses(self):
        """'2 x 120g' — leading count 2, unit g, no trailing multiplier."""
        result = parse_ingredient('2 x 120g')
        # Existing logic: quantity comes from the leading number,
        # the middle 'x 120' is consumed, unit is detected from the remaining 'g'.
        assert result is not None
        assert result['unit'] == 'g'
        # quantity must be a positive number (the leading 2)
        assert result['quantity'] > 0

    def test_plain_quantity_and_unit(self):
        result = parse_ingredient('250g flour')
        assert result is not None
        assert result['quantity'] == pytest.approx(250.0)
        assert result['unit'] == 'g'
        assert 'flour' in result['name']
