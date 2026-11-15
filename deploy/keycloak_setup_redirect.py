"""Allow the exact staff setup return URL without widening login redirects."""
from urllib.parse import urlsplit


def ensure_setup_redirect(request, realm, public_url):
    origin = urlsplit(public_url)
    if (origin.scheme not in ('http', 'https') or not origin.hostname
            or origin.username or origin.password or origin.query or origin.fragment
            or origin.path not in ('', '/')):
        raise ValueError('Public URL must be an HTTP(S) origin')
    redirect = public_url.rstrip('/') + '/login'
    clients = request('/admin/realms/' + realm + '/clients?clientId=arena-ui')
    matches = [client for client in clients if client.get('clientId') == 'arena-ui']
    if len(matches) != 1:
        raise RuntimeError('Expected exactly one arena-ui client')
    client = matches[0]
    redirects = list(client.get('redirectUris', []))
    if redirect not in redirects:
        client['redirectUris'] = redirects + [redirect]
        request('/admin/realms/' + realm + '/clients/' + client['id'], 'PUT', client)
    return redirect
