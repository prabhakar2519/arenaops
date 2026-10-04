#!/usr/bin/env python3
import os
import subprocess
import sys
from config import load_runtime

try:
    values = load_runtime(sys.argv[1])
    if not os.environ.get('DOCKER_CONFIG') or not values.get('GHCR_USERNAME') or not values.get('GHCR_TOKEN'):
        raise ValueError('Temporary registry configuration and credentials are required')
    result = subprocess.run(['docker', 'login', 'ghcr.io', '-u', values['GHCR_USERNAME'], '--password-stdin'],
                            input=values['GHCR_TOKEN'].encode(), capture_output=True, check=False)
    if result.returncode: raise ValueError('GHCR login failed')
    print('Registry authentication succeeded')
except Exception:
    print('Cannot authenticate to GHCR; check environment credentials', file=sys.stderr)
    sys.exit(1)
