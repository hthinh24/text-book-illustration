import { useEffect, useRef } from 'react';

/**
 * Custom polling hook calling fn every intervalMs while enabled is true.
 * @param {Function} fn - Callback to execute on interval
 * @param {Object} options
 * @param {number} [options.intervalMs=2500] - Polling interval in ms
 * @param {boolean} options.enabled - Whether polling is active
 */
export function usePolling(fn, { intervalMs = 2500, enabled }) {
  const fnRef = useRef(fn);
  fnRef.current = fn;

  useEffect(() => {
    if (!enabled) return;
    const id = setInterval(() => {
      fnRef.current();
    }, intervalMs);
    return () => clearInterval(id);
  }, [enabled, intervalMs]);
}
