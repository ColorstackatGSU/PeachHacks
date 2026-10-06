import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Routing is hash based (#/registrations), so the build is one index.html and
// needs no host rewrites.
export default defineConfig({
  plugins: [react()],
  server: { port: 5174, strictPort: true },
  preview: { port: 5174, strictPort: true },
});
