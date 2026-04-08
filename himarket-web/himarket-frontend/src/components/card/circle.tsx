function Circle(
  props: React.PropsWithChildren<{ className?: string; style?: React.CSSProperties }>,
) {
  return (
    <div
      className={`bg-white flex justify-center items-center border border-[#E5E5E5] rounded-full ${props.className}`}
      style={props.style}
    >
      {props.children}
    </div>
  );
}

export default Circle;
