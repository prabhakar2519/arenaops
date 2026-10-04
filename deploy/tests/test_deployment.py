import importlib.util
import json
import os
from pathlib import Path
import shlex
import subprocess
import tempfile
import re
import textwrap
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('app_config', ROOT / 'deploy/config.py')
config = importlib.util.module_from_spec(spec)
spec.loader.exec_module(config)

class DeploymentTests(unittest.TestCase):
    def values(self, environment='sit', **overrides):
        values = {key: 'validation' for key in config.REQUIRED}
        values.update(ARENA_DB_NAME=config.SCHEMAS.get(environment, 'arena_dev'), ARENA_DB_SCHEMA=config.SCHEMAS.get(environment, 'arena_dev'), ARENA_ENV=environment, IMAGE_REPOSITORY='ghcr.io/example/arenaops', IMAGE_TAG='v1.9.0')
        values.update(overrides)
        return values

    def runtime(self, directory, values):
        path = Path(directory) / 'runtime.env'
        path.write_text(''.join(key + '=' + json.dumps(value) + '\n' for key, value in values.items()))
        path.chmod(0o600)
        return path

    def test_compose_environment_network_and_secret_isolation(self):
        for environment, (realm, origin) in config.ENVIRONMENTS.items():
            with self.subTest(environment=environment), tempfile.TemporaryDirectory() as directory:
                secret = '''test$quote"apostrophe'backslash\\# space'''
                path = self.runtime(directory, self.values(environment, ARENA_DB_PASSWORD=secret, KC_BFF_CLIENT_SECRET=secret))
                exports = config.prepare(path)
                result = subprocess.run(['docker', 'compose', '--env-file', '/dev/null', '-p', exports['COMPOSE_PROJECT_NAME'],
                                         '-f', str(ROOT / 'deploy/docker-compose.app.yaml'), 'config', '--format', 'json'],
                                        env=dict(os.environ, **exports), capture_output=True, text=True, check=True)
                composed = json.loads(result.stdout)
                self.assertEqual(composed['name'], 'arenaops-' + environment + '-app')
                self.assertEqual(composed['networks']['arenaops']['name'], 'arenaops-' + environment)
                self.assertTrue(composed['networks']['arenaops']['external'])
                self.assertEqual(exports['ARENAOPS_TARGET'], '/opt/arenaops/' + environment)
                services = composed['services']
                for name, service in services.items():
                    self.assertEqual(service['container_name'], environment + '-' + name)
                    self.assertEqual(set(service['networks']), {'arenaops'})
                    self.assertIn(environment + '-' + name, service['networks']['arenaops']['aliases'])
                    self.assertNotIn('ports', service)
                    self.assertEqual(service['restart'], 'unless-stopped')
                    self.assertIn('healthcheck', service)
                for name in ('arena-core', 'arena-login'):
                    env = services[name]['environment']
                    self.assertEqual(env['SPRING_PROFILES_ACTIVE'], environment)
                    self.assertEqual(env['KC_REALM'], realm)
                    self.assertEqual(env['KC_AUTH_URL'], origin + '/auth')
                core = services['arena-core']['environment']
                self.assertEqual(core['ARENA_DB_PASSWORD'].replace('$$', '$'), secret)
                self.assertEqual(core['KC_BFF_CLIENT_SECRET'].replace('$$', '$'), secret)
                self.assertNotIn('KC_BFF_CLIENT_SECRET', services['arena-login']['environment'])
                self.assertEqual(core['ARENA_DB_SCHEMA'], config.SCHEMAS[environment])
                self.assertEqual(core['ARENA_DB_HOST'], environment + '-postgres')
                self.assertEqual(core['KC_BASE_URL'], 'http://' + environment + '-keycloak:8080/auth')
                self.assertEqual(services['arena-login']['environment']['ARENA_CORE_URL'], 'http://' + environment + '-arena-core:7701')
                self.assertEqual(core['KC_ISSUER_URI'], origin + '/auth/realms/' + realm)
                self.assertEqual(services['arena-login']['environment']['APP_CORS_ALLOWED_ORIGINS'], origin)
                self.assertNotIn('ARENA_DB_PASSWORD', services['arena-login']['environment'])
                self.assertEqual(services['arena-ui']['environment'], {'ARENA_ENV': environment})
                for filename in ('core.env', 'login.env'):
                    self.assertEqual((Path(directory) / filename).stat().st_mode & 0o777, 0o600)

    def test_invalid_environment_missing_credentials_and_mail_fail_fast(self):
        for overrides in ({'ARENA_ENV': 'dev'}, {'ARENA_ENV': 'production'}, {'KC_BFF_CLIENT_SECRET': ''},
                          {'ARENA_DB_PASSWORD': ''}, {'ARENA_DB_SCHEMA': ''}, {'ARENA_DB_SCHEMA': ' '}, {'ARENA_DB_SCHEMA': 'arena'}, {'IMAGE_TAG': 'latest'}, {'IMAGE_REPOSITORY': 'bad;command'},
                          {'ARENAOPS_MAIL_ENABLED': 'true'}, {'ARENAOPS_MAIL_ENABLED': 'maybe'}):
            with self.subTest(overrides=overrides), self.assertRaises(config.ConfigError):
                config.configuration(self.values(**overrides))
        with tempfile.TemporaryDirectory() as directory:
            path = self.runtime(directory, self.values())
            path.chmod(0o644)
            with self.assertRaises(config.ConfigError): config.load_runtime(path)

    def test_changelog_schema_references_are_parameterized(self):
        for path in (ROOT / 'arena-core/src/main/resources/db').rglob('*.xml'):
            text = path.read_text()
            self.assertNotRegex(text, r'\barena\.')
            self.assertNotIn('CREATE SCHEMA IF NOT EXISTS arena;', text)
            for element in ET.fromstring(text).iter():
                for key, value in element.attrib.items():
                    if key.lower().endswith('schemaname'):
                        self.assertEqual(value, '${ARENA_DB_SCHEMA}', (path, key))
        config_text = (ROOT / 'arena-core/src/main/resources/application.yaml').read_text()
        self.assertNotIn('${ARENA_DB_SCHEMA:', config_text)
        self.assertIn('default_schema: ${ARENA_DB_SCHEMA}', config_text)
        self.assertIn('default-schema: ${ARENA_DB_SCHEMA}', config_text)

    def test_schema_missing_and_workflow_injection(self):
        values = self.values()
        del values['ARENA_DB_SCHEMA']
        with self.assertRaisesRegex(config.ConfigError, 'ARENA_DB_SCHEMA is required'):
            config.configuration(values)
        workflow = (ROOT / '.github/workflows/application.yaml').read_text()
        self.assertIn("ARENA_DB_SCHEMA: ${{ github.ref == 'refs/heads/main' && 'arena_sit' || 'arena' }}", workflow)

    def test_runtime_writer_roundtrip_and_no_secret_logs(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'runtime.env'
            secret = '''test$quote"apostrophe'backslash\\# space'''
            values = self.values(KC_BFF_CLIENT_SECRET=secret, GHCR_USERNAME='validation', GHCR_TOKEN='validation-token')
            result = subprocess.run(['python3', str(ROOT / 'deploy/write-runtime-env.py'), str(path)],
                                    env=dict(os.environ, **values), check=True, capture_output=True, text=True)
            self.assertEqual(config.load_runtime(path)['KC_BFF_CLIENT_SECRET'], secret)
            self.assertNotIn(secret, result.stdout + result.stderr)

    def test_remote_failure_cleans_temporary_credentials(self):
        before = set(Path('/dev/shm').glob('arenaops-app.*'))
        with tempfile.TemporaryDirectory() as directory:
            body = self.runtime(directory, self.values()).read_text()
            result = subprocess.run(['bash', str(ROOT / 'deploy/remote-deploy.sh'), 'sit'],
                                    input=body, capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('Invalid application deployment target', result.stderr)
            self.assertNotIn('validation', result.stdout + result.stderr)
        self.assertEqual(set(Path('/dev/shm').glob('arenaops-app.*')), before)

    def test_release_state_changes_only_after_health_success(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / 'sit'
            deploy = target / 'app/deploy'; deploy.mkdir(parents=True)
            state = target / 'state'; state.mkdir()
            (state / 'sit-infrastructure-applied').touch()
            (state / 'current-app-release').write_text('ghcr.io/example/arenaops|v1.8.0\n')
            (deploy / 'deploy-app.sh').write_text((ROOT / 'deploy/deploy-app.sh').read_text())
            # Stub only the external config provider; execute the actual deployment shell state transitions.
            exports = {'ARENAOPS_TARGET': str(target), 'ARENA_ENV': 'sit', 'ARENAOPS_NETWORK': 'arenaops-sit',
                       'COMPOSE_PROJECT_NAME': 'arenaops-sit-app', 'IMAGE_REPOSITORY': 'ghcr.io/example/arenaops', 'IMAGE_TAG': 'v1.9.0'}
            (deploy / 'config.py').write_text('print(' + repr('\n'.join('export ' + k + '=' + shlex.quote(v) for k, v in exports.items())) + ')\n')
            bin_dir = root / 'bin'; bin_dir.mkdir()
            mock = bin_dir / 'docker'
            mock.write_text('#!/bin/sh\ncase "$*" in *"up -d"*) test "${MOCK_DEPLOY_FAIL:-}" != yes ;; *) exit 0 ;; esac\n')
            mock.chmod(0o755)
            env = dict(os.environ, ARENAOPS_APP_ENV_FILE='/unused', PATH=str(bin_dir) + ':' + os.environ['PATH'], MOCK_DEPLOY_FAIL='yes')
            command = ['bash', str(deploy / 'deploy-app.sh')]
            failed = subprocess.run(command, env=env, capture_output=True)
            self.assertNotEqual(failed.returncode, 0)
            self.assertEqual((state / 'current-app-release').read_text(), 'ghcr.io/example/arenaops|v1.8.0\n')
            self.assertFalse((state / 'previous-app-release').exists())
            env['MOCK_DEPLOY_FAIL'] = 'no'
            subprocess.run(command, env=env, capture_output=True, check=True)
            self.assertEqual((state / 'current-app-release').read_text(), 'ghcr.io/example/arenaops|v1.9.0\n')
            self.assertEqual((state / 'previous-app-release').read_text(), 'ghcr.io/example/arenaops|v1.8.0\n')
            subprocess.run(command, env=env, capture_output=True, check=True)
            self.assertEqual((state / 'previous-app-release').read_text(), 'ghcr.io/example/arenaops|v1.8.0\n')

    def test_ui_entrypoint_requires_remote_environment_and_selects_realm(self):
        script = ROOT / 'arena-ui/docker/40-arena-config.sh'
        for environment, (realm, _) in config.ENVIRONMENTS.items():
            with tempfile.TemporaryDirectory() as directory:
                output = Path(directory) / 'arena-config.js'
                subprocess.run(['sh', str(script)], env=dict(os.environ, ARENA_ENV=environment,
                               ARENAOPS_UI_CONFIG_FILE=str(output)), check=True, capture_output=True)
                self.assertIn('keycloakRealm: "' + realm + '"', output.read_text())
        result = subprocess.run(['sh', str(script)], env=dict(os.environ, ARENA_ENV='dev'), capture_output=True)
        self.assertNotEqual(result.returncode, 0)

    def test_production_approval_check_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            gh = Path(directory) / 'gh'
            gh.write_text('#!/bin/sh\n[ "$FAKE_API_FAILURE" = true ] && exit 1\nprintf "%s\\n" "$FAKE_REVIEWERS"\n')
            gh.chmod(0o755)
            for count, failure, success in (('1', 'false', True), ('0', 'false', False), ('', 'true', False)):
                environment = dict(os.environ, PATH=directory + ':' + os.environ['PATH'],
                    GH_TOKEN='test-token', GITHUB_REPOSITORY='example/arenaops',
                    FAKE_REVIEWERS=count, FAKE_API_FAILURE=failure)
                result = subprocess.run(['bash', str(ROOT / 'deploy/verify-production-gate.sh')],
                    env=environment, capture_output=True)
                self.assertEqual(result.returncode == 0, success)

    def test_workflow_event_matrix_and_environment_selection(self):
        workflow = (ROOT / '.github/workflows/application.yaml').read_text()
        for job in ('publish', 'deploy'):
            section = workflow.split('\n  ' + job + ':')[1]
            condition = re.search(r'^    if: (.+)$', section, re.MULTILINE).group(1)
            for event, ref, expected in (
                ('push', 'refs/heads/main', True),
                ('push', 'refs/heads/release/v2.0.0', True),
                ('push', 'refs/heads/feature/test', False),
                ('pull_request', 'refs/pull/1/merge', False),
                ('workflow_dispatch', 'refs/heads/main', False),
                ('workflow_dispatch', 'refs/heads/release/v2.0.0', False),
                ('release', 'refs/tags/v2.0.0', False),
                ('push', 'refs/tags/v2.0.0', False),
            ):
                # Evaluate the actual checked-in event guard against representative GitHub events.
                expression = condition.replace('github.event_name', repr(event)).replace('github.ref', repr(ref))
                expression = expression.replace('&&', 'and').replace('||', 'or')
                result = eval(expression, {'__builtins__': {}, 'startsWith': lambda value, prefix: value.startswith(prefix)})
                self.assertEqual(result, expected, (job, event, ref))
        self.assertIn("TARGET_ENVIRONMENT: ${{ github.ref == 'refs/heads/main' && 'sit' || 'production' }}", workflow)

    def test_published_coordinates_use_commit_sha_for_main_and_validate_release_versions(self):
        workflow = (ROOT / '.github/workflows/application.yaml').read_text()
        step = workflow.split('      - name: Derive immutable release coordinates')[1].split('      - uses:')[0]
        script = textwrap.dedent(step.split('        run: |\n')[1])
        for branch, expected in (('main', 'sha-' + 'a' * 40), ('release/v2.1.0', 'v2.1.0'), ('release/not-a-version', None)):
            with tempfile.TemporaryDirectory() as directory:
                output = Path(directory) / 'output'
                result = subprocess.run(['bash', '-c', script], env=dict(os.environ,
                    GITHUB_REF='refs/heads/' + branch, GITHUB_REF_NAME=branch, GITHUB_SHA='a' * 40,
                    GITHUB_REPOSITORY='Example/ArenaOps', GITHUB_OUTPUT=str(output)), capture_output=True)
                if expected is None:
                    self.assertNotEqual(result.returncode, 0)
                else:
                    self.assertEqual(result.returncode, 0)
                    self.assertIn('image_tag=' + expected, output.read_text())
                    self.assertIn('image_repository=ghcr.io/example/arenaops', output.read_text())

    def test_main_and_release_deploy_only_after_publish_and_no_infrastructure_mutation(self):
        workflow = (ROOT / '.github/workflows/application.yaml').read_text()
        deployment = workflow.split('\n  deploy:')[1]
        self.assertIn("needs: publish", deployment)
        self.assertIn("github.event_name == 'push'", deployment)
        self.assertIn("github.ref == 'refs/heads/main'", deployment)
        self.assertIn("startsWith(github.ref, 'refs/heads/release/v')", deployment)
        self.assertIn("environment: ${{ github.ref == 'refs/heads/main' && 'sit' || 'production' }}", deployment)
        self.assertIn("IMAGE_TAG: ${{ needs.publish.outputs.image_tag }}", deployment)
        self.assertNotIn("workflow_dispatch", deployment)
        self.assertNotIn("inputs.", workflow)
        self.assertIn('image_tag="sha-$GITHUB_SHA"', workflow)
        self.assertIn('cancel-in-progress: false', deployment)
        for script in ('deploy-app.sh', 'deploy-from-actions.sh', 'remote-deploy.sh'):
            content = (ROOT / 'deploy' / script).read_text()
            self.assertNotIn('prepare-vps.sh', content)
            self.assertNotIn('network create', content)
            self.assertNotIn('docker-compose.infra', content)

if __name__ == '__main__': unittest.main()
