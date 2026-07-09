'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import ShoppingListOverlay from '@/app/components/ShoppingListOverlay';
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
                {/* Hidden by globals.css when printing with the overlay open */}
                <div className="shopping-list-print-hide">
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
                </div>

                <ShoppingListOverlay
                    isOpen={isShoppingListOpen}
                    onClose={() => setIsShoppingListOpen(false)}
                    isLoading={isShoppingListLoading}
                    error={shoppingListError}
                    shoppingList={shoppingList}
                />
            </div>
        </div>
    );
}
