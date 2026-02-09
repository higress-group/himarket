interface UserMessageProps {
  text: string;
}

export function UserMessage({ text }: UserMessageProps) {
  return (
    <div className="flex justify-end">
      <div
        className="max-w-[80%] rounded-2xl rounded-tr-md px-4 py-2.5
                      bg-gray-800 text-white text-sm leading-relaxed"
      >
        {text}
      </div>
    </div>
  );
}
