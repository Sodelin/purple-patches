"""Checks that remain active when Python is run with optimization enabled."""


def require(condition, message):
    if not condition:
        raise RuntimeError(message)
