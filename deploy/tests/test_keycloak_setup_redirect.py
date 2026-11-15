import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('setup_redirect', Path(__file__).resolve().parents[1] / 'keycloak_setup_redirect.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SetupRedirectTests(unittest.TestCase):
    def test_exact_redirect_preserves_callbacks_and_is_idempotent(self):
        client = {'id': 'ui', 'clientId': 'arena-ui', 'redirectUris': ['https://sit.arenaops.in/login/callback'], 'publicClient': True}
        writes = []
        def request(path, method='GET', data=None):
            if method == 'PUT':
                writes.append(data.copy())
            else:
                return [client]
        module.ensure_setup_redirect(request, 'arena-sit', 'https://sit.arenaops.in/')
        module.ensure_setup_redirect(request, 'arena-sit', 'https://sit.arenaops.in')
        self.assertEqual(1, len(writes))
        self.assertEqual(['https://sit.arenaops.in/login/callback', 'https://sit.arenaops.in/login'], client['redirectUris'])
        self.assertTrue(client['publicClient'])

    def test_rejects_unsafe_origin_before_api_calls(self):
        for url in ['https://sit.arenaops.in/*', 'https://user:password@example.test', 'https://example.test/?next=bad', 'javascript:bad', 'https://example.test/#bad']:
            with self.subTest(url=url), self.assertRaises(ValueError):
                module.ensure_setup_redirect(lambda *_: self.fail('Unexpected API call'), 'arena-sit', url)

    def test_missing_client_fails_without_writes(self):
        with self.assertRaises(RuntimeError):
            module.ensure_setup_redirect(lambda *_: [], 'arena-sit', 'https://sit.arenaops.in')
