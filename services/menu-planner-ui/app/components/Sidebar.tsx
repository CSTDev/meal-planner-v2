'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useAuth } from '@/lib/auth/auth-context';
import { useState, useEffect, useCallback } from 'react';

const navigation = [
    { name: 'Meal Planner', href: '/meal-plan', icon: '🏠' },
    { name: 'Add Recipe', href: '/scrape', icon: '➕' },
    { name: 'My Recipes', href: '/recipes', icon: '📖' },
    { name: 'Settings', href: '/settings', icon: '⚙️' },
];

export default function Sidebar() {
    const pathname = usePathname();
    const { user, signOut } = useAuth();
    const [isExpanded, setIsExpanded] = useState(false);

    const collapse = useCallback(() => setIsExpanded(false), []);

    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape') collapse();
        };
        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [collapse]);

    if (!user) {
        return null;
    }

    return (
        <>
            {/* ── Mobile layout (hidden at md and above) ── */}
            <div className="md:hidden">
                {/* Backdrop – renders only when expanded */}
                {isExpanded && (
                    <div
                        data-testid="sidebar-backdrop"
                        className="fixed inset-0 bg-black/40 z-20"
                        onClick={collapse}
                        aria-hidden="true"
                    />
                )}

                {/* Expanded overlay – slides in over content */}
                {isExpanded && (
                    <div
                        data-testid="sidebar-mobile-overlay"
                        className="fixed left-0 top-0 h-full w-64 bg-white border-r border-gray-200 z-30 flex flex-col transition-transform duration-300 translate-x-0"
                    >
                        <div className="flex items-center h-16 px-6 border-b border-gray-200">
                            <h1 className="text-xl font-bold text-gray-900">
                                🍳 Recipe Planner
                            </h1>
                        </div>

                        <nav className="flex-1 px-4 py-6 space-y-1">
                            {navigation.map((item) => {
                                const isActive = pathname === item.href;
                                return (
                                    <Link
                                        key={item.name}
                                        href={item.href}
                                        onClick={collapse}
                                        className={`flex items-center px-4 py-3 text-sm font-medium rounded-lg transition-colors ${
                                            isActive
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

                        <div className="border-t border-gray-200 p-4">
                            <div className="flex items-center mb-3">
                                <div className="flex-shrink-0">
                                    <div className="w-8 h-8 bg-blue-100 rounded-full flex items-center justify-center">
                                        <span className="text-sm font-medium text-blue-600">
                                            {user.email?.[0].toUpperCase()}
                                        </span>
                                    </div>
                                </div>
                                <div className="ml-3 flex-1 min-w-0">
                                    <p className="text-sm font-medium text-gray-700 truncate">
                                        {user.user_metadata?.name || user.email}
                                    </p>
                                    <p className="text-xs text-gray-500 truncate">{user.email}</p>
                                </div>
                            </div>
                            <button
                                onClick={() => signOut()}
                                className="w-full text-sm text-left px-4 py-2 text-gray-700 hover:bg-gray-50 rounded-md transition"
                            >
                                Sign Out
                            </button>
                        </div>
                    </div>
                )}

                {/* Always-visible icon-only rail */}
                <div
                    data-testid="sidebar-mobile-rail"
                    className="fixed left-0 top-0 h-full w-14 bg-white border-r border-gray-200 z-30 flex flex-col items-center py-2"
                >
                    {/* Chevron toggle – above the nav icons */}
                    <button
                        onClick={() => setIsExpanded((v) => !v)}
                        aria-label={isExpanded ? 'Close sidebar' : 'Open sidebar'}
                        aria-expanded={isExpanded}
                        className="w-10 h-10 flex items-center justify-center rounded-lg text-gray-700 hover:bg-gray-50 mb-2 text-lg"
                    >
                        {isExpanded ? '‹' : '›'}
                    </button>

                    {navigation.map((item) => {
                        const isActive = pathname === item.href;
                        return (
                            <Link
                                key={item.name}
                                href={item.href}
                                aria-label={item.name}
                                className={`w-10 h-10 flex items-center justify-center rounded-lg text-xl mb-1 ${
                                    isActive ? 'bg-blue-50' : 'hover:bg-gray-50'
                                }`}
                            >
                                {item.icon}
                            </Link>
                        );
                    })}
                </div>
            </div>

            {/* ── Desktop / tablet layout (md and above) – unchanged ── */}
            <div
                data-testid="sidebar-desktop"
                className="hidden md:flex flex-col w-64 bg-white border-r border-gray-200"
            >
                <div className="flex items-center h-16 px-6 border-b border-gray-200">
                    <h1 className="text-xl font-bold text-gray-900">
                        🍳 Recipe Planner
                    </h1>
                </div>

                <nav className="flex-1 px-4 py-6 space-y-1">
                    {navigation.map((item) => {
                        const isActive = pathname === item.href;
                        return (
                            <Link
                                key={item.name}
                                href={item.href}
                                className={`flex items-center px-4 py-3 text-sm font-medium rounded-lg transition-colors ${
                                    isActive
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

                <div className="border-t border-gray-200 p-4">
                    <div className="flex items-center mb-3">
                        <div className="flex-shrink-0">
                            <div className="w-8 h-8 bg-blue-100 rounded-full flex items-center justify-center">
                                <span className="text-sm font-medium text-blue-600">
                                    {user.email?.[0].toUpperCase()}
                                </span>
                            </div>
                        </div>
                        <div className="ml-3 flex-1 min-w-0">
                            <p className="text-sm font-medium text-gray-700 truncate">
                                {user.user_metadata?.name || user.email}
                            </p>
                            <p className="text-xs text-gray-500 truncate">{user.email}</p>
                        </div>
                    </div>
                    <button
                        onClick={() => signOut()}
                        className="w-full text-sm text-left px-4 py-2 text-gray-700 hover:bg-gray-50 rounded-md transition"
                    >
                        Sign Out
                    </button>
                </div>
            </div>
        </>
    );
}
