import { useState, useEffect, useRef, createRef } from "react";

// Define the props type for the generalized Switch component
interface MultiSwitchProps {
  loading?: boolean;
  // Array of option strings
  options: { label: React.ReactNode, value: string }[];
  // Callback function when the switch state changes
  onChange?: (value: string) => void;
  // Initial value for the switch, should match one of the options
  initialValue?: string;
  // Optional class name for custom styling of the container
  className?: string;
  // Optional class name for custom styling of the active background
  activeBgClassName?: string;
  // Optional class name for custom styling of the buttons
  buttonClassName?: string;
  // Optional class name for custom styling of the active button text
  activeButtonClassName?: string;
  // Optional class name for custom styling of the inactive button text
  inactiveButtonClassName?: string;
}

// Interface for storing button dimension info
interface ButtonDimension {
  width: number;
  offsetLeft: number;
}

const MultiSwitchButton: React.FC<MultiSwitchProps> = ({
  loading = false,
  options,
  onChange,
  initialValue,
  className = '',
  activeBgClassName = 'bg-white', // Default active background color
  buttonClassName = '',
  activeButtonClassName = 'text-mainTitle', // Default active text color
  inactiveButtonClassName = 'text-mainTitle hover:bg-white/80', // Default inactive text color
}) => {
  // Refs for each button to measure dimensions
  const buttonRefs = useRef<(HTMLButtonElement | null)[]>([]);
  // Ensure refs array has the correct length
  if (buttonRefs.current.length !== options.length) {
    buttonRefs.current = Array(options.length).fill(null).map((_, i) => buttonRefs.current[i] || createRef<HTMLButtonElement>().current);
  }

  // State to store measured dimensions of buttons
  const [buttonDimensions, setButtonDimensions] = useState<ButtonDimension[]>([]);
  // State to track the active index
  const [activeIndex, setActiveIndex] = useState<number>(() => {
    const initialIdx = initialValue ? options.findIndex(option => option.value === initialValue) : 0;
    return initialIdx !== -1 ? initialIdx : 0;
  });
  // State to store the container's left padding (needed for offset calculation)
  const [containerPaddingLeft, setContainerPaddingLeft] = useState(4); // Default p-1 = 4px
  const containerRef = useRef<HTMLDivElement>(null);


  // Effect to measure button dimensions after render or when options change
  useEffect(() => {
    if (loading) return;
    const measureDimensions = () => {
      if (containerRef.current) {
        // Get container padding dynamically
        const styles = window.getComputedStyle(containerRef.current);
        setContainerPaddingLeft(parseFloat(styles.paddingLeft) || 0);
      }

      const dimensions = buttonRefs.current.map(ref => {
        if (ref) {
          return { width: ref.offsetWidth, offsetLeft: ref.offsetLeft };
        }
        return { width: 0, offsetLeft: 0 }; // Default if ref is null
      });
      // Only update if dimensions actually changed to prevent potential loops
      if (JSON.stringify(dimensions) !== JSON.stringify(buttonDimensions)) {
        setButtonDimensions(dimensions);
      }
    };

    measureDimensions(); // Initial measurement

    // Optional: Re-measure on window resize for responsiveness
    window.addEventListener('resize', measureDimensions);
    return () => window.removeEventListener('resize', measureDimensions);

    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [options, loading]); // Re-run if options array changes

  // Effect to update activeIndex if initialValue prop changes externally
  useEffect(() => {
    if (loading) return;
    const newIndex = initialValue ? options.findIndex(option => option.value === initialValue) : 0;
    if (newIndex !== -1 && newIndex !== activeIndex) {
      setActiveIndex(newIndex);
    }
    // We only want this effect to run when initialValue changes, not activeIndex itself
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialValue, options, loading]);

  // Function to handle the click event and update the active index
  const handleToggle = (index: number) => {
    if (index !== activeIndex) {
      setActiveIndex(index);
      if (onChange) {
        onChange(options[index].value);
      }
    }
  };

  // Get dimensions for the currently active button
  const activeDimension = buttonDimensions[activeIndex];
  const backgroundWidth = activeDimension ? activeDimension.width : 0;
  // Calculate translateX: button's offsetLeft minus container's left padding
  const backgroundTranslateX = activeDimension ? activeDimension.offsetLeft - containerPaddingLeft : 0;

  if (loading) {
    // Render a skeleton loader matching the switch's appearance
    return (
      <div className={`inline-flex bg-gray-200 rounded-lg p-1 select-none animate-pulse ${className}`}>
        {/* Adjust height and width as needed for your loading state */}
        <div className="h-8 w-32 rounded-lg bg-gray-300"></div>
      </div>
    );
  }

  console.log(buttonDimensions, 'activeButtonClassName...')

  return (
    // Main container: uses inline-flex to fit content width, rounded full for capsule shape
    // p-1 adds padding inside the container
    <div ref={containerRef} className={`gap-1 relative inline-flex bg-[#f5f5f5] rounded-lg p-1 select-none ${className}`}>
      {/* Animated background element */}
      {/* Positioned absolutely within the container, accounting for padding with top-1, left-1 */}
      {/* Height adjusted to fill space within padding: h-[calc(100%-8px)] */}
      {/* Width and Transform are now dynamic based on measured button dimensions */}
      <div
        className={`absolute top-1 left-1 h-[calc(100%-8px)] rounded-lg shadow-md transition-transform duration-300 ease-in-out ${activeBgClassName}`}
        style={{
          width: `${backgroundWidth}px`, // Use measured width
          transform: `translateX(${backgroundTranslateX}px)`, // Use calculated offset
          // Add opacity transition for smoother initial appearance
          opacity: backgroundWidth > 0 ? 1 : 0,
          transitionProperty: 'transform, width, opacity', // Ensure width transition is also animated
        }}
      ></div>

      {/* Map over options to render buttons */}
      {/* REMOVED flex-1 to allow natural width */}
      {options.map((option, index) => (
        <button
          // Assign ref to each button
          ref={el => buttonRefs.current[index] = el}
          key={option.value}
          onClick={() => handleToggle(index)}
          // Base button styles: relative z-index ensures text is above background
          // Centering, transitions, padding (px-3 py-1.5)
          // Dynamic text color based on active state
          className={`relative cursor-pointer z-10 flex items-center justify-center rounded-lg px-6 py-1.5 text-sm font-medium transition-colors duration-300 ease-in-out whitespace-nowrap ${buttonClassName} ${index === activeIndex ? activeButtonClassName : inactiveButtonClassName
            }`}
        // Disable button if it's already active
        // disabled={index === activeIndex}
        // minWidth removed, allowing button to shrink/grow freely based on content + padding
        >
          {option.label} {/* Display option text */}
        </button>
      ))}
    </div>
  );
};

export default MultiSwitchButton;