import { SearchOutlined } from '@ant-design/icons';
import { Button, Input } from 'antd';

export interface SearchInputProps {
  placeholder?: string;
  value: string;
  onChange: (value: string) => void;
  onSearch: () => void;
  onClear?: () => void;
  width?: number;
}

export function SearchInput({
  onChange,
  onClear,
  onSearch,
  placeholder,
  value,
  width = 260,
}: SearchInputProps) {
  return (
    <div
      className="flex items-center border border-gray-300 rounded-md overflow-hidden hover:border-blue-500 focus-within:border-blue-500 transition-colors"
      style={{ width }}
    >
      <Input
        allowClear
        className="border-0"
        onChange={(e) => onChange(e.target.value)}
        onClear={onClear}
        onPressEnter={onSearch}
        placeholder={placeholder}
        size="middle"
        value={value}
        variant="borderless"
      />
      <Button
        className="border-0 rounded-none"
        icon={<SearchOutlined />}
        onClick={onSearch}
        style={{ width: 40 }}
        type="text"
      />
    </div>
  );
}
