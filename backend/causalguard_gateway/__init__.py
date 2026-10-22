"""CausalGuard minimal model gateway.

The gateway keeps the upstream model credential on the server and exposes only
an OpenAI-compatible ``POST /v1/chat/completions`` endpoint to the Android app.
"""

__version__ = "0.1.0"
