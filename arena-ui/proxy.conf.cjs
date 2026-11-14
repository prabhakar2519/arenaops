const backend = { target: 'http://localhost:7700', changeOrigin: true };

module.exports = {
  '/api/**': { ...backend, changeOrigin: false },
  '/oauth2/**': backend,
  '/logout': backend,
  '/auth/**': {
    target: 'http://localhost:9091',
    changeOrigin: true,
    selfHandleResponse: true,
    configure(proxy) {
      proxy.on('proxyReq', request => request.setHeader('accept-encoding', 'identity'));
      proxy.on('proxyRes', (upstream, request, response) => {
        const origin = `http://${request.headers.host}`;
        const publicUrl = value => value
          .replaceAll('http://localhost:9091', origin)
          .replaceAll('http://127.0.0.1:9091', origin)
          .replace(/(^|["'(=\s]|https?:\/\/[^/'"\s]+)(\/(?:admin|realms|resources)(?=\/|[?#"' ]|$))/g,
            (match, prefix, path) => prefix.startsWith('http') && prefix !== origin
              ? match : `${prefix}/auth${path}`);
        const chunks = [];
        upstream.on('data', chunk => chunks.push(chunk));
        upstream.on('error', () => response.destroy());
        upstream.on('end', () => {
          const headers = { ...upstream.headers };
          delete headers['content-length'];
          delete headers['transfer-encoding'];
          if (headers.location) headers.location = publicUrl(headers.location);
          // This proxy is used only by the HTTP DEV server. Keycloak considers
          // its localhost upstream secure, but LAN HTTP browsers reject Secure
          // cookies. Same-host DEV requests work with HttpOnly and SameSite=Lax.
          if (headers['set-cookie']) {
            headers['set-cookie'] = headers['set-cookie'].map(cookie => cookie
              // Keycloak scopes realm cookies to its upstream path, without the browser-facing prefix.
              .replace(/;\s*Path=(\/realms(?:\/[^;]*)?)/i, ';Path=/auth$1')
              .replace(/;\s*Secure\b/gi, '').replace(/SameSite=None/gi, 'SameSite=Lax'));
          }
          let body = Buffer.concat(chunks);
          // Keep JSON discovery/token issuers canonical for backend validation.
          // Rewrite browser URLs only, preserving Keycloak's /auth context path.
          if (headers['content-type']?.includes('text/html')) {
            body = Buffer.from(publicUrl(body.toString('utf8')));
          }
          response.writeHead(upstream.statusCode || 502, headers);
          response.end(body);
        });
      });
    }
  }
};
