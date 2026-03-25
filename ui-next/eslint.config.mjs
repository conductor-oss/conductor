import eslintReact from "@eslint-react/eslint-plugin";
import js from "@eslint/js";
import vitest from "@vitest/eslint-plugin";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import { globalIgnores } from "eslint/config";
import globals from "globals";
import tseslint from "typescript-eslint";

const commonRules = {
  "@typescript-eslint/no-explicit-any": "warn",
  "@typescript-eslint/no-unused-vars": [
    "error",
    { argsIgnorePattern: "^_", varsIgnorePattern: "^_" },
  ],
  // TODO: Remove this and fix types properly
  "@typescript-eslint/ban-ts-comment": "warn",
  // Prevent direct imports from date-fns and date-fns-tz except in utils/date.ts
  "no-restricted-imports": [
    "error",
    {
      patterns: [
        {
          group: ["date-fns"],
          message:
            "Direct imports from 'date-fns' are not allowed. Please import from 'src/utils/date' instead.",
        },
        {
          group: ["date-fns-tz"],
          message:
            "Direct imports from 'date-fns-tz' are not allowed. Please import from 'src/utils/date' instead.",
        },
      ],
    },
  ],
};

const baseConfig = {
  extends: [
    js.configs.recommended,
    tseslint.configs.recommended,
    reactHooks.configs["recommended-latest"],
    reactRefresh.configs.vite,
    eslintReact.configs.recommended,
  ],
  languageOptions: {
    ecmaVersion: 2020,
    sourceType: "module",
    globals: {
      ...globals.browser,
      ...globals.node,
    },
  },
  rules: {
    "no-undef": "error",
    ...commonRules,
  },
};

export default tseslint.config([
  globalIgnores(["dist", "node_modules"]),

  // Test files (Vitest + testing globals)
  {
    files: [
      "**/__tests__/**/*.{js,jsx,ts,tsx}",
      "**/*.{test,spec}.{js,jsx,ts,tsx}",
    ],
    ...baseConfig,
    plugins: { vitest, ...baseConfig.plugins },
    rules: {
      ...vitest.configs.recommended.rules,
      ...commonRules,
    },
  },

  // JSX files (allow PropTypes)
  {
    files: ["**/*.jsx"],
    ...baseConfig,
    rules: {
      ...baseConfig.rules,
      "react/prop-types": "off",
      "@eslint-react/no-prop-types": "off",
    },
  },

  // Non-test files (TS/TSX)
  {
    files: ["**/*.{js,ts,tsx}"],
    ignores: [
      "**/__tests__/**/*.{js,jsx,ts,tsx}",
      "**/*.{test,spec}.{js,jsx,ts,tsx}",
    ],
    ...baseConfig,
  },

  // Allow date-fns and date-fns-tz imports in utils/date.ts
  {
    files: ["src/utils/date.ts"],
    rules: {
      "no-restricted-imports": "off",
    },
  },
]);
