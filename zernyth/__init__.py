"""Zernyth distribution identity; native compatibility imports remain in endstone."""

from importlib.metadata import version


def get_version() -> str:
    """Return the installed Python distribution version (PEP 440)."""
    return version("zernyth-engine")
