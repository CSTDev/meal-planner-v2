'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

const navigation = [
    { name: 'Meal Planner', href: '/meal-plan', icon: '🏠' },
    { name: 'Add Recipe', href: '/scrape', icon: '➕' },
    { name: 'My Recipes', href: '/recipes', icon: '📖' },
    { name: 'Settings', href: '/settings', icon: '⚙️' },
];

export default function Sidebar() {
    const pathname = usePathname();

    return (
        <div className="flex flex-col w-64 bg-white border-r border-gray-200">
            {/* Logo/Brand */}
            <div className="flex items-center h-16 px-6 border-b border-gray-200">
                <h1 className="text-xl font-bold text-gray-900">
                    🍳 Recipe Planner
                </h1>
            </div>

            {/* Navigation */}
            <nav className="flex-1 px-4 py-6 space-y-1">
                {navigation.map((item) => {
                    const isActive = pathname === item.href;

                    return (
                        <Link
                            key={item.name}
                            href={item.href}
                            className={`flex items-center px-4 py-3 text-sm font-medium rounded-lg transition-colors ${isActive
                                    ? 'bg-blue-50 text-blue-700'
                                    : 'text-gray-700 hover:bg-gray-50 hover:text-gray-900'
                                }`}
                        >
                            <span className="mr-3 text-xl">{item.icon}</span>
                            {item.name}
                        </Link>
                    );
                })}
            </nav>

            {/* User Profile */}
            <div className="flex items-center px-6 py-4 border-t border-gray-200">
                <div className="flex items-center">
                    <div className="flex-shrink-0">
                        <div className="w-8 h-8 bg-gray-300 rounded-full flex items-center justify-center">
                            <span className="text-sm font-medium text-gray-600">👤</span>
                        </div>
                    </div>
                    <div className="ml-3">
                        <p className="text-sm font-medium text-gray-700">User</p>
                        <p className="text-xs text-gray-500">View profile</p>
                    </div>
                </div>
            </div>
        </div>
    );
}