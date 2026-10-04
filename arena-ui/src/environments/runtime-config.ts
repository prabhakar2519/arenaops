export type ArenaEnvironment = 'dev' | 'sit' | 'prod';

export interface ArenaRuntimeConfig {
  arenaEnv: ArenaEnvironment;
  keycloakRealm: string;
  keycloakBaseUrl: string;
}

declare global {
  interface Window { ARENAOPS_CONFIG?: ArenaRuntimeConfig; }
}

export function validateRuntimeConfig(value: ArenaRuntimeConfig | undefined): ArenaRuntimeConfig {
  const realms: Record<ArenaEnvironment, string> = { dev: 'arena-dev', sit: 'arena-sit', prod: 'arena' };
  if (!value || !Object.hasOwn(realms, value.arenaEnv) || value.keycloakRealm !== realms[value.arenaEnv]) {
    throw new Error('ArenaOps environment configuration is missing or inconsistent.');
  }
  const url = new URL(value.keycloakBaseUrl);
  if (url.pathname !== '/auth' || url.search || url.hash || url.username || url.password
      || (value.arenaEnv !== 'dev' && url.protocol !== 'https:')) {
    throw new Error('ArenaOps identity URL is invalid.');
  }
  return value;
}
