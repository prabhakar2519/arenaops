#!/usr/bin/env python3
"""Read runtime secrets as data and derive one environment-specific application map."""
import json
import os
from pathlib import Path
import re
import shlex
import stat
import sys

REQUIRED = ('ARENA_DB_SCHEMA', 'ARENA_DB_NAME', 'ARENA_DB_USERNAME', 'ARENA_DB_PASSWORD', 'KC_BFF_CLIENT_SECRET')
OPTIONAL_DEFAULTS = {
    'ARENAOPS_MAIL_ENABLED': 'false',
    'BREVO_SMTP_HOST': 'smtp-relay.brevo.com', 'BREVO_SMTP_PORT': '587',
    'BREVO_SMTP_USERNAME': '', 'BREVO_SMTP_PASSWORD': '', 'ARENAOPS_MAIL_FROM': '',
    'ARENAOPS_MAIL_FROM_NAME': 'ArenaOps', 'ARENAOPS_MAIL_REPLY_TO': '',
    'ARENAOPS_BILLING_MONTHLY': '1000', 'ARENAOPS_BILLING_ANNUAL': '10000',
    'ARENAOPS_BILLING_TAX_RATE': '0.18',
}
ENVIRONMENTS = {'sit': ('arena-sit', 'https://sit.arenaops.in'), 'prod': ('arena', 'https://arenaops.in')}

SCHEMAS = {'sit': 'arena_sit', 'prod': 'arena'}

class ConfigError(ValueError):
    pass

def load_runtime(filename):
    path = Path(filename)
    if not path.is_file() or stat.S_IMODE(path.stat().st_mode) != 0o600:
        raise ConfigError('Runtime env file must exist with mode 0600')
    values = {}
    allowed = set(REQUIRED) | set(OPTIONAL_DEFAULTS) | {'ARENA_ENV', 'IMAGE_REPOSITORY', 'IMAGE_TAG', 'GHCR_USERNAME', 'GHCR_TOKEN'}
    for line in path.read_text().splitlines():
        if not line or line.startswith('#'):
            continue
        key, sep, value = line.partition('=')
        if not sep or key not in allowed or key in values:
            raise ConfigError('Invalid, unsupported or duplicate runtime env key')
        # The pipeline writes JSON strings, not an executable shell/legacy dotenv bundle.
        value = json.loads(value)
        if not isinstance(value, str) or any(c in value for c in '\r\n\x00'):
            raise ConfigError('Runtime values must be single-line strings')
        values[key] = value
    return values

def configuration(values):
    environment = values.get('ARENA_ENV')
    if environment not in ENVIRONMENTS:
        raise ConfigError('Application deployment supports only sit or prod')
    for key in REQUIRED + ('IMAGE_REPOSITORY', 'IMAGE_TAG'):
        if not values.get(key) or not values[key].strip():
            raise ConfigError(key + ' is required')
    if not re.fullmatch(r'ghcr\.io/[a-z0-9][a-z0-9._/-]*', values['IMAGE_REPOSITORY']):
        raise ConfigError('IMAGE_REPOSITORY must be a GHCR repository path')
    if not re.fullmatch(r'(v[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?|sha-[0-9a-f]{40})', values['IMAGE_TAG']):
        raise ConfigError('IMAGE_TAG must be a reviewed release version or full commit SHA tag')
    if values['ARENA_DB_SCHEMA'] != SCHEMAS[environment]:
        raise ConfigError('ARENA_DB_SCHEMA must be ' + SCHEMAS[environment] + ' for ' + environment)
    realm, origin = ENVIRONMENTS[environment]
    common = {key: values[key] for key in REQUIRED}
    common.update(ARENA_ENV=environment, KC_REALM=realm, KC_BASE_URL='http://' + environment + '-keycloak:8080/auth',
                  KC_AUTH_URL=origin + '/auth', KC_JWK_SET_URI='http://' + environment + '-keycloak:8080/auth/realms/' + realm + '/protocol/openid-connect/certs',
                  KC_ISSUER_URI=origin + '/auth/realms/' + realm)
    core = dict(common, **{key: values.get(key) or default for key, default in OPTIONAL_DEFAULTS.items()})
    core.update(ARENA_DB_HOST=environment + '-postgres', ARENAOPS_FRONTEND_URL=origin, APP_BASE_URL=origin)
    if core['ARENAOPS_MAIL_ENABLED'] not in ('true', 'false'):
        raise ConfigError('ARENAOPS_MAIL_ENABLED must be true or false')
    if core['ARENAOPS_MAIL_ENABLED'] == 'true':
        for key in ('BREVO_SMTP_USERNAME', 'BREVO_SMTP_PASSWORD', 'ARENAOPS_MAIL_FROM'):
            if not core[key]:
                raise ConfigError(key + ' is required when email is enabled')
    login = {key: value for key, value in common.items() if not key.startswith('ARENA_DB_') and key != 'KC_BFF_CLIENT_SECRET'}
    login.update(APP_BASE_URL=origin, APP_CORS_ALLOWED_ORIGINS=origin, SESSION_COOKIE_SECURE='true', ARENA_CORE_URL='http://' + environment + '-arena-core:7701')
    compose = dict(ARENA_ENV=environment, COMPOSE_PROJECT_NAME='arenaops-' + environment + '-app',
                   ARENAOPS_NETWORK='arenaops-' + environment, ARENAOPS_TARGET='/opt/arenaops/' + environment,
                   IMAGE_REPOSITORY=values['IMAGE_REPOSITORY'], IMAGE_TAG=values['IMAGE_TAG'])
    return core, login, compose

def write_module_env(path, values):
    # Compose env_file format: raw preserves literal quotes, hashes, dollars and backslashes.
    with path.open('x') as output:
        for key, value in values.items():
            output.write(key + '=' + value + '\n')
    path.chmod(0o600)

def prepare(filename):
    os.umask(0o077)
    path = Path(filename).resolve()
    core, login, compose = configuration(load_runtime(path))
    core_path, login_path = path.parent / 'core.env', path.parent / 'login.env'
    write_module_env(core_path, core)
    write_module_env(login_path, login)
    compose.update(CORE_RUNTIME_ENV_FILE=str(core_path), LOGIN_RUNTIME_ENV_FILE=str(login_path))
    return compose

if __name__ == '__main__':
    try:
        for key, value in prepare(sys.argv[1]).items():
            print('export ' + key + '=' + shlex.quote(value))
    except ConfigError as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(1)
    except Exception:
        print('Cannot prepare application runtime configuration', file=sys.stderr)
        sys.exit(1)
