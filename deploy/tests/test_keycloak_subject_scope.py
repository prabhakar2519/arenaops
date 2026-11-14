import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('keycloak_subject_scope', Path(__file__).resolve().parents[1] / 'keycloak_subject_scope.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SubjectScopeTests(unittest.TestCase):
    def connection(self, assigned=(), missing_client=None, mapper=True):
        calls = []
        basic = {'id': 'scope-basic', 'name': 'basic', 'protocol': 'openid-connect'}
        existing = set(assigned)
        def request(path, method='GET', data=None):
            calls.append((method, path))
            if method == 'PUT':
                existing.add('arena-ui' if '/ui/' in path else 'arena-bff')
                return None
            if path.endswith('/protocol-mappers/models'):
                return [{'protocolMapper': 'oidc-sub-mapper', 'config': {'access.token.claim': 'true'}}] if mapper else []
            if path.endswith('/client-scopes'):
                return [basic]
            if '/clients?clientId=' in path:
                name = path.split('=')[-1]
                return [] if name == missing_client else [{'id': 'ui' if name == 'arena-ui' else 'bff', 'clientId': name, 'protocol': 'openid-connect'}]
            name = 'arena-ui' if '/ui/' in path else 'arena-bff'
            return [basic, {'id': 'roles', 'name': 'roles'}] if name in existing else [{'id': 'roles', 'name': 'roles'}]
        return request, calls

    def test_check_is_read_only(self):
        request, calls = self.connection()
        self.assertEqual(['arena-ui', 'arena-bff'], module.ensure_subject_scope(request, 'arena-sit')['missing'])
        self.assertTrue(all(method == 'GET' for method, _ in calls))

    def test_apply_adds_only_missing_scope_and_is_idempotent(self):
        request, calls = self.connection(assigned=['arena-ui'])
        self.assertTrue(module.ensure_subject_scope(request, 'arena-sit', apply=True)['applied'])
        self.assertEqual([('PUT', '/admin/realms/arena-sit/clients/bff/default-client-scopes/scope-basic')], [call for call in calls if call[0] != 'GET'])
        calls.clear()
        self.assertEqual([], module.ensure_subject_scope(request, 'arena-sit', apply=True)['missing'])
        self.assertTrue(all(method == 'GET' for method, _ in calls))

    def test_preflight_checks_both_clients_before_any_write(self):
        request, calls = self.connection(missing_client='arena-bff')
        with self.assertRaises(module.SubjectScopeError):
            module.ensure_subject_scope(request, 'arena-sit', apply=True)
        self.assertTrue(all(method == 'GET' for method, _ in calls))

    def test_missing_subject_mapper_fails_closed(self):
        request, calls = self.connection(mapper=False)
        with self.assertRaises(module.SubjectScopeError):
            module.ensure_subject_scope(request, 'arena-sit', apply=True)
        self.assertTrue(all(method == 'GET' for method, _ in calls))
