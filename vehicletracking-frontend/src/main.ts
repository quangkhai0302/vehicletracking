// Shared Vue entry for development and production.
import { createApp } from 'vue';
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
app.provide(authKey, auth).use(router).mount('#root');
if (import.meta.hot) import.meta.hot.dispose(() => { disposeGuards(); app.unmount(); });
