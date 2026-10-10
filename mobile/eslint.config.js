const expoConfig = require("eslint-config-expo/flat");

const jestGlobals = Object.fromEntries(
  ["afterEach", "beforeEach", "describe", "expect", "jest", "test"].map((name) => [name, "readonly"]),
);

module.exports = [
  ...expoConfig,
  {
    files: ["**/__tests__/**/*.js"],
    languageOptions: { globals: jestGlobals },
  },
  {
    ignores: ["node_modules/**", "dist/**", "android/**", "ios/**", ".expo/**"],
  },
];
