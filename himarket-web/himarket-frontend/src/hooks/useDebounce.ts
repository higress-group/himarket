import { useEffect, useRef } from 'react';

/**
 * 通用 debounce Hook，在 value 变化后延迟指定时间执行回调。
 * 如果在延迟期间 value 再次变化，则重置计时器。
 * 首次挂载时不会触发回调，只在后续 value 变化时才执行。
 *
 * @param value - 要监听的值
 * @param delay - 延迟时间（毫秒）
 * @param callback - 延迟后执行的回调函数
 */
export function useDebounce<T>(value: T, delay: number, callback: (value: T) => void) {
  const callbackRef = useRef(callback);

  useEffect(() => {
    callbackRef.current = callback;
  }, [callback]);

  const timerRef = useRef<ReturnType<typeof setTimeout>>();
  const isFirstRender = useRef(true);

  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      return;
    }
    timerRef.current = setTimeout(() => callbackRef.current(value), delay);
    return () => clearTimeout(timerRef.current);
  }, [value, delay]);
}
