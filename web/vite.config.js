import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const rootDir = dirname(fileURLToPath(import.meta.url));

// auth.html and sponsors.html load script.js as a classic (non-module)
// script. Vite leaves that tag untouched and does not copy the file, so emit
// it verbatim at the dist root where the unchanged <script src> expects it.
const copyClassicScript = {
  name: "copy-classic-script",
  apply: "build",
  generateBundle() {
    this.emitFile({
      type: "asset",
      fileName: "script.js",
      source: readFileSync(resolve(rootDir, "script.js"), "utf8"),
    });
  },
};

// Every top-level HTML page is its own entry point; without this list Vite
// only builds index.html and the other pages 404 in production.
export default defineConfig({
  plugins: [react(), copyClassicScript],
  build: {
    rollupOptions: {
      input: {
        main: resolve(rootDir, "index.html"),
        auth: resolve(rootDir, "auth.html"),
        interestForm: resolve(rootDir, "interest-form.html"),
        sponsorForm: resolve(rootDir, "sponsor-form.html"),
        sponsors: resolve(rootDir, "sponsors.html"),
      },
    },
  },
});
