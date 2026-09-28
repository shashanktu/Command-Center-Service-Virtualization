import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 8080,
    proxy: {
      '/api': 'vam-service-virtualisation-ftada5d6eqgzaphb.eastus-01.azurewebsites.net',
      '/virtualize': 'vam-service-virtualisation-ftada5d6eqgzaphb.eastus-01.azurewebsites.net'
    }
  }
});
