import { useState, useCallback, useEffect, useRef } from 'react';

interface UseResizableOptions {
  direction: 'horizontal' | 'vertical';
  defaultSize: number;
  minSize: number;
  maxSize: number;
  storageKey?: string;
  /** When true, dragging in the negative direction increases size (e.g. drag up to grow terminal). */
  reverse?: boolean;
}

interface UseResizableReturn {
  size: number;
  isDragging: boolean;
  handleMouseDown: (e: React.MouseEvent) => void;
}

function readStorage(key: string, fallback: number): number {
  try {
    const raw = localStorage.getItem(key);
    if (raw !== null) {
      const val = Number(raw);
      if (Number.isFinite(val)) return val;
    }
  } catch {
    // ignore
  }
  return fallback;
}

function writeStorage(key: string, value: number): void {
  try {
    localStorage.setItem(key, String(value));
  } catch {
    // ignore
  }
}

export function useResizable({
  defaultSize,
  direction,
  maxSize,
  minSize,
  reverse = false,
  storageKey,
}: UseResizableOptions): UseResizableReturn {
  const [size, setSize] = useState(() =>
    storageKey ? readStorage(storageKey, defaultSize) : defaultSize,
  );
  const [isDragging, setIsDragging] = useState(false);

  const startPosRef = useRef(0);
  const startSizeRef = useRef(0);
  const sizeRef = useRef(size);

  useEffect(() => {
    sizeRef.current = size;
  }, [size]);

  const clamp = useCallback(
    (val: number) => Math.min(maxSize, Math.max(minSize, val)),
    [minSize, maxSize],
  );

  const handleMouseDown = useCallback(
    (e: React.MouseEvent) => {
      e.preventDefault();
      startPosRef.current = direction === 'horizontal' ? e.clientX : e.clientY;
      startSizeRef.current = sizeRef.current;
      setIsDragging(true);

      const cursor = direction === 'horizontal' ? 'col-resize' : 'row-resize';
      document.body.style.cursor = cursor;
      document.body.style.userSelect = 'none';
    },
    [direction],
  );

  useEffect(() => {
    if (!isDragging) return;

    const onMouseMove = (e: MouseEvent) => {
      const currentPos = direction === 'horizontal' ? e.clientX : e.clientY;
      const delta = currentPos - startPosRef.current;
      const newSize = clamp(startSizeRef.current + (reverse ? -delta : delta));
      setSize(newSize);
    };

    const onMouseUp = () => {
      setIsDragging(false);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
      if (storageKey) {
        writeStorage(storageKey, sizeRef.current);
      }
    };

    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
    return () => {
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
  }, [isDragging, direction, reverse, clamp, storageKey]);

  return { handleMouseDown, isDragging, size };
}
