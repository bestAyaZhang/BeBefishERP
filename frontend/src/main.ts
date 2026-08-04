import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import { setUnauthorizedHandler } from './services/http';
import './assets/main.css';

setUnauthorizedHandler(() => {
  void router.push({ name: 'login' });
});
createApp(App).use(router).mount('#app');
