"""
Pytest configuration: mock optional heavy dependencies so the pure-Python
parsing helpers in main.py can be imported without Kafka, Prometheus or
recipe_scrapers being installed in the test environment.
"""
import sys
from unittest.mock import MagicMock

for _mod in ('recipe_scrapers', 'kafka', 'kafka.errors', 'prometheus_client'):
    if _mod not in sys.modules:
        sys.modules[_mod] = MagicMock()
