import eslint from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'

export default tseslint.config(
  {
    ignores: ['dist/**', 'node_modules/**', 'src/api/types/generated.ts'],
  },
  eslint.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    files: ['*.vue', '**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser,
      },
      globals: {
        // 浏览器定时器与 DOM 全局：Vue SFC 运行在浏览器环境。
        setInterval: 'readonly',
        clearInterval: 'readonly',
        setTimeout: 'readonly',
        clearTimeout: 'readonly',
        window: 'readonly',
        document: 'readonly',
        fetch: 'readonly',
        File: 'readonly',
        FormData: 'readonly',
        URLSearchParams: 'readonly',
      },
    },
  },
  {
    rules: {
      'vue/multi-word-component-names': 'off',
      // Prettier owns template wrapping; keep ESLint focused on correctness.
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      // Prettier self-closes void/empty HTML elements; align ESLint with it.
      'vue/html-self-closing': [
        'error',
        { html: { void: 'always', normal: 'always', component: 'always' }, svg: 'always', math: 'always' },
      ],
    },
  },
)
