import vue from 'eslint-plugin-vue';
import tseslint from 'typescript-eslint';

// Oxlint handles plain TS/JS. This pass also checks Vue template semantics.
export default [
  ...vue.configs['flat/essential'],
  {
    files: ['**/*.vue'],
    languageOptions: { parserOptions: { parser: tseslint.parser, sourceType: 'module' } },
    rules: { 'vue/multi-word-component-names': 'off' },
  },
];
