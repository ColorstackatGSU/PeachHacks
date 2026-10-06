import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const rootDir = dirname(fileURLToPath(import.meta.url));

// Every top-level HTML page is its own entry point; without this list Vite
// only builds index.html and the other pages 404 in production.
export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      input: {
        main: resolve(rootDir, "index.html"),
        interestForm: resolve(rootDir, "interest-form.html"),
        sponsorForm: resolve(rootDir, "sponsor-form.html"),
      },
    },
  },
});
