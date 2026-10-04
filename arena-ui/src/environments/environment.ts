import { validateRuntimeConfig } from './runtime-config';

const runtime = validateRuntimeConfig(window.ARENAOPS_CONFIG);
export const environment = {
  production: runtime.arenaEnv !== 'dev',
  arenaEnv: runtime.arenaEnv,
  keycloakRealm: runtime.keycloakRealm,
  keycloakBaseUrl: runtime.keycloakBaseUrl
};
