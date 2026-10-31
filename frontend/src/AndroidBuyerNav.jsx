import { ClipboardList, ShoppingBasket, ShoppingCart, Tag, UserRound } from 'lucide-react';
import AndroidScrollableNav from './AndroidScrollableNav.jsx';

const ITEMS = [
  { id: 'browse', label: 'खरेदी', icon: ShoppingBasket },
  { id: 'offers', label: 'विशेष ऑफर', icon: Tag },
  { id: 'cart', label: 'कार्ट', icon: ShoppingCart },
  { id: 'orders', label: 'ऑर्डर', icon: ClipboardList },
  { id: 'profile', label: 'प्रोफाइल', icon: UserRound }
];

export default function AndroidBuyerNav({ activeTab, view, unreadOrders = 0, onOpenView, onProfile }) {
  return <AndroidScrollableNav activeKey={`${activeTab}:${view}`} className="android-buyer-nav" ariaLabel="खरेदीदार अॅप नेव्हिगेशन">
    {ITEMS.map(item => {
      const Icon = item.icon;
      const active = item.id === 'profile' ? activeTab === 'profile'
        : activeTab === 'marketplace' && view === item.id;
      const onClick = item.id === 'profile' ? onProfile : () => onOpenView(item.id);
      return <button type="button" key={item.id}
        className={`android-bottom-nav-item ${active ? 'active' : ''}`}
        onClick={onClick} aria-label={item.label} aria-current={active ? 'page' : undefined}>
        <span className="android-bottom-icon"><Icon size={21}/>{item.id === 'orders' && unreadOrders > 0 && <b className="android-nav-badge" aria-label={`${unreadOrders.toLocaleString('mr-IN')} न वाचलेले संदेश`}>{unreadOrders > 99 ? '99+' : unreadOrders.toLocaleString('mr-IN')}</b>}</span>
        <span>{item.label}</span>
      </button>;
    })}
  </AndroidScrollableNav>;
}
