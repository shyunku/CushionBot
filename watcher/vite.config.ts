import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// In development, API calls go to a running bot (or the synthetic API) on WATCHER_API.
const api = process.env.WATCHER_API ?? "http://localhost:7918";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": api,
      "/sse": api,
    },
  },
});
