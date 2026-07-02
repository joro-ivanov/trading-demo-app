/**
 * Frontend architecture boundaries (the TS-native equivalent of ArchUnit — docs/DESIGN.md §13.3).
 * Enforces layering: the data layer (api/ws) must not depend on UI components, and no cycles.
 */
module.exports = {
  forbidden: [
    {
      name: 'no-circular',
      severity: 'error',
      comment: 'No circular dependencies.',
      from: {},
      to: { circular: true },
    },
    {
      name: 'data-layer-not-import-ui',
      severity: 'error',
      comment: 'api/ws (data layer) must not depend on React components.',
      from: { path: '^src/(api|ws)/' },
      to: { path: '^src/components/' },
    },
    {
      name: 'no-orphans',
      severity: 'warn',
      comment: 'Modules should be reachable.',
      from: { orphan: true, pathNot: ['\\.d\\.ts$', 'vite-env'] },
      to: {},
    },
  ],
  options: {
    doNotFollow: { path: 'node_modules' },
    // Test files (Vitest *.test.* and WebdriverIO *.wdio.tsx) are not part of the app graph.
    exclude: { path: '\\.(test|wdio)\\.tsx?$' },
    tsConfig: { fileName: 'tsconfig.json' },
    tsPreCompilationDeps: true,
  },
}
