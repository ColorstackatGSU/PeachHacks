import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Routing is hash based (#/hackers), so the build is one index.html and needs no host
// rewrites. Discord sends people back to the bare origin with ?code=, which that page reads.
export default defineConfig({
  plugins: [react()],
  server: { port: 5176, strictPort: true },
  preview: { port: 5176, strictPort: true },
});
