import type { AggregatedLogEntry } from '../../types/log';

/** 按 method 或 summary 过滤日志（不区分大小写） */
export function filterLogs(logs: AggregatedLogEntry[], filter: string): AggregatedLogEntry[] {
  if (!filter) return logs;
  const q = filter.toLowerCase();
  return logs.filter((entry) => {
    const method = (entry.method ?? '').toLowerCase();
    const summary = entry.summary.toLowerCase();
    return method.includes(q) || summary.includes(q);
  });
}

/** 检查日志列表是否按 timestamp 升序排列 */
export function isTimeSorted(logs: AggregatedLogEntry[]): boolean {
  for (let i = 1; i < logs.length; i++) {
    const current = logs[i]?.timestamp;
    const previous = logs[i - 1]?.timestamp;
    if (current === undefined || previous === undefined || current < previous) return false;
  }
  return true;
}
