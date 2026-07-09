'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import ShoppingList from '@/app/components/ShoppingList';
import {
    AcceptedRecipe,
    getAcceptedRecipes,
    getShoppingList,
} from '@/lib/api/mealPlans';
import { ShoppingListResponse } from '@/types/recipe';

export default function PastPlanDetailPage() {
    const params = useParams<{ id: string }>();
    const planId = params.id;

    const [recipes, setRecipes] = useState<AcceptedRecipe[] | null>(null);
    const [error, setError] = useState<string | null>(null);

    const [isShoppingListOpen, setIsShoppingListOpen] = useState(false);
    const [shoppingList, setShoppingList] = useState<ShoppingListResponse | null>(null);
    const [isShoppingListLoading, setIsShoppingListLoading] = useState(false);
    const [shoppingListError, setShoppingListError] = useState<string | null>(null);

    useEffect(() => {
        if (!planId) return;
        getAcceptedRecipes(planId)
            .then(setRecipes)
            .catch((err) => {
                console.error('Failed to load accepted recipes:', err);
                setError('Failed to load this meal plan.');
            });
    }, [planId]);

    const handleGenerateShoppingList = async () => {
        setIsShoppingListOpen(true);
        setIsShoppingListLoading(true);
        setShoppingListError(null);
        try {
            const data = await getShoppingList(planId);
            setShoppingList(data);
        } catch (err) {
            console.error('Failed to get shopping list:', err);
            setShoppingListError('Failed to load shopping list.');
        } finally {
            setIsShoppingListLoading(false);
        }
    };

    return (
        <div className="py-12 px-4 sm:px-6 lg:px-8">
            <div className="max-w-3xl mx-auto">
                <div className="mb-8">
                    <Link
                        href="/past-plans"
                        className="text-sm text-blue-600 hover:text-blue-800"
                    >
                        &larr; Back to past plans
                    </Link>
                    <div className="mt-4 flex items-center justify-between">
                        <h1 className="text-3xl font-bold text-gray-900">Past Plan</h1>
                        <button
                            onClick={handleGenerateShoppingList}
                            disabled={!recipes || recipes.length === 0}
                            className="px-4 py-2 border border-gray-300 rounded-md text-gray-700 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            Generate shopping list
                        </button>
                    </div>
                </div>

                {error && (
                    <p role="alert" className="text-red-600 text-center">
                        {error}
                    </p>
                )}

                {!recipes && !error && (
                    <p className="text-gray-600 text-center">Loading meal plan...</p>
                )}

                {recipes && recipes.length === 0 && (
                    <p className="text-gray-600 text-center">
                        This plan has no accepted recipes.
                    </p>
                )}

                {recipes && recipes.length > 0 && (
                    <ul className="bg-white rounded-lg border border-gray-200 divide-y divide-gray-200">
                        {recipes.map((recipe) => (
                            <li key={recipe.id} className="px-6 py-4">
                                <p className="font-medium text-gray-900">{recipe.title}</p>
                                {recipe.description && (
                                    <p className="mt-1 text-sm text-gray-600 line-clamp-2">
                                        {recipe.description}
                                    </p>
                                )}
                            </li>
                        ))}
                    </ul>
                )}

                {isShoppingListOpen && (
                    <div className="shopping-list-overlay fixed inset-0 z-50 flex justify-end">
                        <div
                            className="shopping-list-backdrop absolute inset-0 bg-black/40"
                            onClick={() => setIsShoppingListOpen(false)}
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
                                        onClick={() => setIsShoppingListOpen(false)}
                                        aria-label="Close shopping list"
                                        className="text-gray-500 hover:text-gray-700 text-2xl leading-none"
                                    >
                                        &times;
                                    </button>
                                </div>
                            </div>

                            {isShoppingListLoading && (
                                <p className="text-gray-600">Loading shopping list...</p>
                            )}

                            {shoppingListError && (
                                <p className="text-red-600">{shoppingListError}</p>
                            )}

                            {!isShoppingListLoading && !shoppingListError && shoppingList && (
                                <ShoppingList data={shoppingList} />
                            )}
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}
