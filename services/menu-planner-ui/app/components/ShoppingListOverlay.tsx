'use client';

import { useEffect } from 'react';
import ShoppingList from '@/app/components/ShoppingList';
import { ShoppingListResponse } from '@/types/recipe';

interface ShoppingListOverlayProps {
    isOpen: boolean;
    onClose: () => void;
    isLoading: boolean;
    error: string | null;
    shoppingList: ShoppingListResponse | null;
}

/**
 * Slide-over panel showing a meal plan's shopping list.
 *
 * While open it sets `shopping-list-open` on <body>, which the
 * `@media print` rules in globals.css rely on to hide the rest of the
 * page and unclip the layout so the printed list can span multiple
 * pages. Page content that should not be printed while the overlay is
 * open must carry the `shopping-list-print-hide` class (or one of the
 * page-specific classes already targeted in globals.css).
 */
export default function ShoppingListOverlay({
    isOpen,
    onClose,
    isLoading,
    error,
    shoppingList,
}: ShoppingListOverlayProps) {
    useEffect(() => {
        if (isOpen) {
            document.body.classList.add('shopping-list-open');
        } else {
            document.body.classList.remove('shopping-list-open');
        }
        return () => document.body.classList.remove('shopping-list-open');
    }, [isOpen]);

    if (!isOpen) {
        return null;
    }

    return (
        <div className="shopping-list-overlay fixed inset-0 z-50 flex justify-end">
            <div
                className="shopping-list-backdrop absolute inset-0 bg-black/40"
                onClick={onClose}
            />
            <div className="shopping-list-pane relative w-full max-w-md bg-white h-full shadow-xl overflow-y-auto p-6">
                <div className="flex items-center justify-between mb-4">
                    <h3 className="text-xl font-bold text-gray-900">Shopping List</h3>
                    <div className="shopping-list-controls flex items-center gap-2">
                        <button
                            type="button"
                            onClick={() => window.print()}
                            aria-label="Print shopping list"
                            className="text-gray-500 hover:text-gray-700"
                        >
                            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                                <polyline points="6 9 6 2 18 2 18 9"/>
                                <path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/>
                                <rect x="6" y="14" width="12" height="8"/>
                            </svg>
                        </button>
                        <button
                            type="button"
                            onClick={onClose}
                            aria-label="Close shopping list"
                            className="text-gray-500 hover:text-gray-700 text-2xl leading-none"
                        >
                            &times;
                        </button>
                    </div>
                </div>

                {isLoading && (
                    <p className="text-gray-600">Loading shopping list...</p>
                )}

                {error && <p className="text-red-600">{error}</p>}

                {!isLoading && !error && shoppingList && (
                    <ShoppingList data={shoppingList} />
                )}
            </div>
        </div>
    );
}
