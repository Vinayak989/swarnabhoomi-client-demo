import { useEffect, useRef } from 'react';

export default function AndroidScrollableNav({ activeKey, ariaLabel, className = '', children }) {
  const navRef = useRef(null);

  useEffect(() => {
    const nav = navRef.current;
    const active = nav?.querySelector('[aria-current="page"]');
    if (!active) return;
    const navBounds = nav.getBoundingClientRect();
    const itemBounds = active.getBoundingClientRect();
    if (itemBounds.left < navBounds.left || itemBounds.right > navBounds.right) {
      nav.scrollTo({
        left: nav.scrollLeft + itemBounds.left - navBounds.left - (nav.clientWidth - itemBounds.width) / 2,
        behavior: 'auto'
      });
    }
  }, [activeKey]);

  return <nav ref={navRef} className={`android-bottom-nav ${className}`} aria-label={ariaLabel}>
    {children}
  </nav>;
}
