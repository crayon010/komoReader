import { useRouter } from 'vue-router';

import { activateLibrary } from '@/stores/tabs';

/** 回到阅读库：tab 保持打开，随时可切回来。ReaderTabBar/ReaderView/BookView 共用 */
export function useGoLibrary() {
  const router = useRouter();
  return () => {
    activateLibrary();
    router.push('/');
  };
}
