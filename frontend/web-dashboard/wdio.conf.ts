// WebdriverIO browser runner: mounts React components in a real headless Firefox and drives them.
// This is the FE *component* layer — separate from the Java Playwright e2e UI suite (which hits the
// docker-served app on :8088). The `react` preset reuses @vitejs/plugin-react to bundle the specs.
//
// The browser is configurable through environment variables, so the same config runs locally and in a
// container (see Dockerfile.test and the devcontainer, which install Mozilla's official Firefox and
// geckodriver for amd64 and arm64 via scripts/install-firefox.sh):
//   FIREFOX_BIN       Firefox binary to drive. Unset: WebdriverIO finds or downloads Firefox.
//   GECKODRIVER_PATH  geckodriver to use. Unset: WebdriverIO downloads geckodriver.
//   WDIO_HEADLESS     "false" opens a visible browser for local debugging. Default: headless.
const firefoxBinary = process.env.FIREFOX_BIN
const geckodriverBinary = process.env.GECKODRIVER_PATH
const headless = process.env.WDIO_HEADLESS !== 'false'

const firefoxArgs = [...(headless ? ['-headless'] : []), '-width=1280', '-height=800']

export const config: WebdriverIO.Config = {
  runner: ['browser', { preset: 'react' }],
  specs: ['./src/**/*.wdio.tsx'],
  maxInstances: 1,
  capabilities: [
    {
      browserName: 'firefox',
      'moz:firefoxOptions': {
        args: firefoxArgs,
        ...(firefoxBinary ? { binary: firefoxBinary } : {}),
      },
      ...(geckodriverBinary ? { 'wdio:geckodriverOptions': { binary: geckodriverBinary } } : {}),
    },
  ],
  framework: 'mocha',
  reporters: ['spec'],
  mochaOpts: { ui: 'bdd', timeout: 60000 },
  logLevel: 'warn',
  // A headless browser needs no virtual display, so skip looking for xvfb-run.
  autoXvfb: !headless,
}
