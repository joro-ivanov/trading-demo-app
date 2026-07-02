// WebdriverIO browser runner: mounts React components in a real headless Chrome and drives them.
// This is the FE *component* layer — separate from the Java Playwright e2e UI suite (which hits the
// docker-served app on :8088). The `react` preset reuses @vitejs/plugin-react to bundle the specs.
export const config: WebdriverIO.Config = {
  runner: ['browser', { preset: 'react' }],
  specs: ['./src/**/*.wdio.tsx'],
  maxInstances: 1,
  capabilities: [
    {
      browserName: 'chrome',
      'goog:chromeOptions': {
        args: ['headless', 'disable-gpu', 'no-sandbox', 'window-size=1280,800'],
      },
    },
  ],
  framework: 'mocha',
  reporters: ['spec'],
  mochaOpts: { ui: 'bdd', timeout: 60000 },
  logLevel: 'warn',
}
