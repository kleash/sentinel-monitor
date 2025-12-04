export const environment = {
  production: false,
  /**
   * Base URL for the platform service. In mock mode the interceptor answers locally,
   * so this is unused but kept for parity with deployed environments.
   */
  apiBaseUrl: 'http://localhost:8080',
  wsBaseUrl: 'ws://localhost:8080/ws',
  mockApi: true,
  pollIntervalMs: 4000,
  wallboardAutoRefreshMs: 8000,
  timezone: 'local',
  /**
   * Feature flag to keep the UI usable without a running backend. Turn off in real deployments.
   */
  mockWallboardWorkflowKey: 'trade-lifecycle'
};
