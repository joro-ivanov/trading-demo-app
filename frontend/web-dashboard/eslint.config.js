import js from '@eslint/js'
import boundaries from 'eslint-plugin-boundaries'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import globals from 'globals'
import tseslint from 'typescript-eslint'

// Frontend linting (docs/DESIGN.md §13.3): Rules of Hooks + layer boundaries (data must not import
// UI), complementing dependency-cruiser. Boundaries mirror the React best-practices layering.
export default tseslint.config(
  { ignores: ['dist', 'node_modules'] },
  {
    files: ['src/**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
      boundaries,
    },
    settings: {
      'boundaries/elements': [
        { type: 'app', pattern: 'src/App.tsx', mode: 'file' },
        { type: 'ui', pattern: 'src/components/*', mode: 'file' },
        { type: 'data', pattern: 'src/{api,ws}/*', mode: 'file' },
        { type: 'shared', pattern: 'src/types.ts', mode: 'file' },
      ],
    },
    rules: {
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      'boundaries/element-types': [
        'error',
        {
          default: 'allow',
          rules: [{ from: ['data'], disallow: ['ui'], message: 'Data layer must not import UI components' }],
        },
      ],
    },
  },
  // Test files (Vitest *.test.* + WebdriverIO *.wdio.tsx): test/node globals, and they are exempt
  // from layering/react-refresh rules (they reach across layers and export helpers by design).
  {
    files: ['src/**/*.test.{ts,tsx}', 'src/**/*.wdio.tsx'],
    languageOptions: {
      globals: { ...globals.browser, ...globals.node, ...globals.mocha },
    },
    rules: {
      'boundaries/element-types': 'off',
      'react-refresh/only-export-components': 'off',
      'no-restricted-syntax': [
        'error',
        { selector: 'IfStatement', message: 'Tests must be linear: no if/else control flow.' },
        { selector: 'ForStatement', message: 'Tests must be linear: no for loops.' },
        { selector: 'ForInStatement', message: 'Tests must be linear: no for-in loops.' },
        { selector: 'ForOfStatement', message: 'Tests must be linear: no for-of loops.' },
        { selector: 'WhileStatement', message: 'Tests must be linear: no while loops.' },
        { selector: 'DoWhileStatement', message: 'Tests must be linear: no do/while loops.' },
        { selector: 'SwitchStatement', message: 'Tests must be linear: no switch statements.' },
        { selector: 'TryStatement', message: 'Tests must be linear: no try/catch control flow.' },
        { selector: 'ConditionalExpression', message: 'Tests must be linear: no ternary branching.' },
      ],
    },
  },
)
