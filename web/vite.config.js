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
        confirmEmail: resolve(rootDir, "confirm-email.html"),
        privacy: resolve(rootDir, "privacy.html"),
        sponsorForm: resolve(rootDir, "sponsor-form.html"),
        terms: resolve(rootDir, "terms.html"),
        ticket: resolve(rootDir, "ticket.html"),
        unsubscribe: resolve(rootDir, "unsubscribe.html"),
      },
    },
  },
});
