import dayjs, { Dayjs } from 'dayjs';
import quarterOfYear from 'dayjs/plugin/quarterOfYear';

// 扩展dayjs支持季度
dayjs.extend(quarterOfYear);

/**
 * 时间格式常量
 */
export const DATETIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';
export const DATE_FORMAT = 'YYYY-MM-DD';
export const TIME_FORMAT = 'HH:mm:ss';

/**
 * 预设时间范围类型
 */
export type PresetTimeRange =
  | '1m' | '5m' | '15m' | '1h' | '4h' | '1d'
  | 'today' | 'yesterday' | 'dayBeforeYesterday'
  | '1w' | 'thisWeek' | 'lastWeek'
  | '30d' | 'thisMonth' | 'lastMonth'
  | 'thisQuarter' | 'thisYear';

/**
 * 格式化日期时间为本地字符串
 * @param date Date对象或Dayjs对象
 * @param format 格式字符串，默认为 'YYYY-MM-DD HH:mm:ss'
 * @returns 格式化后的字符串
 */
export function formatDatetimeLocal(
  date: Date | Dayjs | string | number,
  format: string = DATETIME_FORMAT
): string {
  return dayjs(date).format(format);
}

/**
 * 获取预设时间范围
 * @param preset 预设类型
 * @returns [开始时间, 结束时间]
 */
export function getPresetTimeRange(preset: PresetTimeRange): [Dayjs, Dayjs] {
  const now = dayjs();
  
  switch (preset) {
    // 相对时间范围
    case '1m':
      return [now.subtract(1, 'minute'), now];
    case '5m':
      return [now.subtract(5, 'minute'), now];
    case '15m':
      return [now.subtract(15, 'minute'), now];
    case '1h':
      return [now.subtract(1, 'hour'), now];
    case '4h':
      return [now.subtract(4, 'hour'), now];
    case '1d':
      return [now.subtract(1, 'day'), now];
    case '1w':
      return [now.subtract(7, 'day'), now];
    case '30d':
      return [now.subtract(30, 'day'), now];
    
    // 绝对时间范围
    case 'today':
      return [now.startOf('day'), now.endOf('day')];
    case 'yesterday':
      return [
        now.subtract(1, 'day').startOf('day'),
        now.subtract(1, 'day').endOf('day')
      ];
    case 'dayBeforeYesterday':
      return [
        now.subtract(2, 'day').startOf('day'),
        now.subtract(2, 'day').endOf('day')
      ];
    case 'thisWeek':
      return [now.startOf('week'), now.endOf('week')];
    case 'lastWeek':
      return [
        now.subtract(1, 'week').startOf('week'),
        now.subtract(1, 'week').endOf('week')
      ];
    case 'thisMonth':
      return [now.startOf('month'), now.endOf('month')];
    case 'lastMonth':
      return [
        now.subtract(1, 'month').startOf('month'),
        now.subtract(1, 'month').endOf('month')
      ];
    case 'thisQuarter':
      return [now.startOf('quarter' as any), now.endOf('quarter' as any)];
    case 'thisYear':
      return [now.startOf('year'), now.endOf('year')];
    
    default:
      return [now.subtract(7, 'day'), now];
  }
}

/**
 * 生成时间范围快捷选项（用于DatePicker.RangePicker）
 */
export const rangePresets: {
  label: string;
  value: [Dayjs, Dayjs];
}[] = [
  { label: '最近1分钟', value: getPresetTimeRange('1m') },
  { label: '最近5分钟', value: getPresetTimeRange('5m') },
  { label: '最近15分钟', value: getPresetTimeRange('15m') },
  { label: '最近1小时', value: getPresetTimeRange('1h') },
  { label: '最近4小时', value: getPresetTimeRange('4h') },
  { label: '最近1天', value: getPresetTimeRange('1d') },
  { label: '今天', value: getPresetTimeRange('today') },
  { label: '昨天', value: getPresetTimeRange('yesterday') },
  { label: '前天', value: getPresetTimeRange('dayBeforeYesterday') },
  { label: '最近1周', value: getPresetTimeRange('1w') },
  { label: '本周', value: getPresetTimeRange('thisWeek') },
  { label: '上周', value: getPresetTimeRange('lastWeek') },
  { label: '最近30天', value: getPresetTimeRange('30d') },
  { label: '本月', value: getPresetTimeRange('thisMonth') },
  { label: '上月', value: getPresetTimeRange('lastMonth') },
  { label: '本季度', value: getPresetTimeRange('thisQuarter') },
  { label: '本年度', value: getPresetTimeRange('thisYear') },
];

/**
 * 计算时间范围的简短标签（用于表格显示）
 * @param startTime 开始时间
 * @param endTime 结束时间
 * @returns 简短标签，如 "1h", "3d", "1w"
 */
export function getTimeRangeLabel(startTime: string | Dayjs, endTime: string | Dayjs): string {
  const start = dayjs(startTime);
  const end = dayjs(endTime);
  const diffMinutes = end.diff(start, 'minute');
  
  if (diffMinutes < 60) {
    return `${diffMinutes}m`;
  } else if (diffMinutes < 60 * 24) {
    const hours = Math.floor(diffMinutes / 60);
    return `${hours}h`;
  } else if (diffMinutes < 60 * 24 * 7) {
    const days = Math.floor(diffMinutes / (60 * 24));
    return `${days}d`;
  } else {
    const weeks = Math.floor(diffMinutes / (60 * 24 * 7));
    return `${weeks}w`;
  }
}

/**
 * 格式化数值（添加千分位分隔符）
 * @param value 数值
 * @returns 格式化后的字符串
 */
export function formatNumber(value: number | string): string {
  const num = typeof value === 'string' ? parseFloat(value) : value;
  if (isNaN(num)) return String(value);
  return num.toLocaleString('en-US');
}
