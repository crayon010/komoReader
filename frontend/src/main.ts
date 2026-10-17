import { createApp } from 'vue';

import App from './App.vue';
import router from './router';

import './style.css';

// 主题在样式加载后、挂载前设置，避免闪一下错误配色
document.documentElement.dataset.theme = localStorage.getItem('theme') ?? 'dark';

createApp(App).use(router).mount('#app');
