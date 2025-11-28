import { ArrowUpOutlined } from "@ant-design/icons";
import { Button } from "antd";
import { useCallback, useEffect, useState } from "react";

export default function BackToTopButton({ container }: { container?: HTMLDivElement }) {
  const [isVisible, setIsVisible] = useState(false);

  // Show button when page is scrolled down
  const toggleVisibility = useCallback(() => {
    const top = container?.scrollTop || window.scrollY;
    // Show button after scrolling 300px, adjust as needed
    if (top > 300) {
      setIsVisible(true);
    } else {
      setIsVisible(false);
    }
  }, [container?.scrollTop]);

  // Smooth scroll to top
  const scrollToTop = () => {
    const c = container || window;
    c.scrollTo({
      top: 0,
      behavior: "smooth",
    });
  };

  useEffect(() => {
    const con = container || window;
    con.addEventListener("scroll", toggleVisibility);

    // Cleanup listener on component unmount
    return () => {
      con.removeEventListener("scroll", toggleVisibility);
    };
  }, [container, toggleVisibility]);

  return (
    <Button
      type="primary"
      className={`fixed bottom-8 right-2 rounded-full h-10 w-10 shadow-lg transition-opacity duration-300 ease-in-out z-50
        hover:scale-110 active:scale-95 
        ${isVisible ? "opacity-100" : "opacity-0 pointer-events-none"
        }
      )`}
      onClick={scrollToTop}
      aria-label="Scroll to top"
    >
      <ArrowUpOutlined className="h-6 w-6" />
    </Button>
  );
}
