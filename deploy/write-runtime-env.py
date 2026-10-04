#!/usr/bin/env python3
import json
import os
from pathlib import Path
import sys
from config import REQUIRED, OPTIONAL_DEFAULTS, configuration

os.umask(0o077)
values = {key: os.environ.get(key, '') for key in REQUIRED + ('ARENA_ENV', 'IMAGE_TAG', 'IMAGE_REPOSITORY', 'GHCR_USERNAME', 'GHCR_TOKEN')}
values.update({key: os.environ.get(key) or default for key, default in OPTIONAL_DEFAULTS.items()})
try:
    for key, value in values.items():
        if any(c in value for c in '\r\n\x00'):
            raise ValueError(key + ' must be single-line')
    for key in ('GHCR_USERNAME', 'GHCR_TOKEN'):
        if not values[key]: raise ValueError(key + ' is required')
    configuration(values)
    with Path(sys.argv[1]).open('x') as output:
        for key, value in values.items(): output.write(key + '=' + json.dumps(value) + '\n')
except ValueError as exc:
    print(str(exc), file=sys.stderr)
    sys.exit(1)
