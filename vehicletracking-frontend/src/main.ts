// Shared Vue entry for development and production.
import { createApp } from 'vue';
import Vue3Toastify from 'vue3-toastify';
import 'vue3-toastify/dist/index.css';
import App from './App.vue';
import { createApplicationRouter } from './app/router';
import { createAuthState } from '@/features/auth/composables/authState';
import { authKey } from '@/features/auth/composables/useAuth';
import { installAuthGuards } from './app/router/guards';
import './index.css';
import './workspace.css';
import './ui-refresh.css';

const auth = createAuthState(), router = createApplicationRouter();
const disposeGuards = installAuthGuards(router, auth);
const app = createApp(App);
app
  .provide(authKey, auth)
  .use(router)
  .use(Vue3Toastify, {
    autoClose: 4000,
    clearOnUrlChange: false,
    limit: 4,
    newestOnTop: true,
    position: 'top-right',
    theme: 'colored',
  })
  .mount('#root');
if (import.meta.hot) import.meta.hot.dispose(() => { disposeGuards(); app.unmount(); });
