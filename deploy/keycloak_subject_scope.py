"""Repair the required subject scope without changing realm policies or identities."""
from urllib.parse import quote


class SubjectScopeError(ValueError):
    pass


def ensure_subject_scope(request, realm, apply=False):
    """Preflight both app clients; attach only a missing built-in basic scope.

    request(path, method='GET', data=None) must use an authenticated Keycloak
    Admin API connection. Check mode is read-only. No secrets enter the result.
    """
    base = '/admin/realms/' + quote(realm, safe='')
    scopes = request(base + '/client-scopes')
    matches = [scope for scope in scopes if scope.get('name') == 'basic']
    if len(matches) != 1 or not matches[0].get('id') or matches[0].get('protocol') != 'openid-connect':
        raise SubjectScopeError('The built-in basic OpenID Connect scope is missing or ambiguous.')
    scope_id = quote(matches[0]['id'], safe='')
    mappers = request(base + '/client-scopes/' + scope_id + '/protocol-mappers/models')
    if not any(mapper.get('protocolMapper') == 'oidc-sub-mapper'
               and mapper.get('config', {}).get('access.token.claim') == 'true' for mapper in mappers):
        raise SubjectScopeError('The basic scope must include the access-token subject mapper.')
    pending = []
    for name in ('arena-ui', 'arena-bff'):
        clients = request(base + '/clients?clientId=' + name)
        clients = [client for client in clients if client.get('clientId') == name]
        if len(clients) != 1 or not clients[0].get('id') or clients[0].get('protocol') != 'openid-connect':
            raise SubjectScopeError('Required OpenID Connect client is missing or ambiguous: ' + name)
        path = base + '/clients/' + quote(clients[0]['id'], safe='') + '/default-client-scopes'
        assigned = request(path)
        if not any(scope.get('id') == matches[0]['id'] for scope in assigned):
            pending.append((name, path + '/' + scope_id))
    if apply:
        for _, path in pending:
            request(path, method='PUT')
    return {'realm': realm, 'missing': [name for name, _ in pending], 'applied': apply and bool(pending)}
