'use client';

import { useState, useEffect } from 'react';
import RecipeCard from '@/app/components/RecipeCard';
import RecipeSelector from '@/app/components/RecipeSelector';
import ShoppingList from '@/app/components/ShoppingList';
import { recordFeedback, getMealPlanRecommendations, getShoppingList } from '@/lib/api/mealPlans';
import { MealPlan, Recipe, ShoppingListResponse } from '@/types/recipe';

interface MealPlanViewProps {
    mealPlan: MealPlan;
    onMealPlanUpdated: (mealPlan: MealPlan) => void;
    onReset: () => void;
}

export default function MealPlanView({
    mealPlan,
    onMealPlanUpdated,
    onReset
}: MealPlanViewProps) {
    const [replacingIndex, setReplacingIndex] = useState<number | null>(null);
    const [isShoppingListOpen, setIsShoppingListOpen] = useState(false);
    const [shoppingList, setShoppingList] = useState<ShoppingListResponse | null>(null);
    const [isShoppingListLoading, setIsShoppingListLoading] = useState(false);
    const [shoppingListError, setShoppingListError] = useState<string | null>(null);
    const [acceptedRecipeIds, setAcceptedRecipeIds] = useState<Set<string>>(new Set());
    const [acceptError, setAcceptError] = useState<string | null>(null);

    const acceptedCount = acceptedRecipeIds.size;
    const totalCount = mealPlan.recipes.length;

    useEffect(() => {
        if (isShoppingListOpen) {
            document.body.classList.add('shopping-list-open');
        } else {
            document.body.classList.remove('shopping-list-open');
        }
        return () => document.body.classList.remove('shopping-list-open');
    }, [isShoppingListOpen]);

    const handleReject = async (recipe: Recipe, index: number) => {
        setAcceptError(null);
        try {
            // Record rejection
            await recordFeedback(mealPlan.id, recipe.id, 'rejected');

            // Remove from accepted set if it was accepted
            setAcceptedRecipeIds(prev => {
                const next = new Set(prev);
                next.delete(recipe.id);
                return next;
            });

            // Get replacement
            const replacements = await getMealPlanRecommendations(mealPlan.id, 1);

            if (replacements.length > 0) {
                const updatedRecipes = [...mealPlan.recipes];
                updatedRecipes[index] = replacements[0];

                onMealPlanUpdated({
                    ...mealPlan,
                    recipes: updatedRecipes,
                });
            }
        } catch (error) {
            console.error('Failed to reject recipe:', error);
        }
    };

    const handleAccept = async (recipe: Recipe) => {
        // Guard against duplicate accepts (idempotent in UI)
        if (acceptedRecipeIds.has(recipe.id)) return;

        // Optimistic update
        setAcceptedRecipeIds(prev => new Set([...prev, recipe.id]));
        setAcceptError(null);

        try {
            await recordFeedback(mealPlan.id, recipe.id, 'accepted');
        } catch (error) {
            // Revert on failure
            setAcceptedRecipeIds(prev => {
                const next = new Set(prev);
                next.delete(recipe.id);
                return next;
            });
            setAcceptError('Failed to accept recipe. Please try again.');
            console.error('Failed to accept recipe:', error);
        }
    };

    const handleViewShoppingList = async () => {
        setIsShoppingListOpen(true);
        setIsShoppingListLoading(true);
        setShoppingListError(null);
        try {
            const data = await getShoppingList(mealPlan.id);
            setShoppingList(data);
        } catch (error) {
            console.error('Failed to get shopping list:', error);
            setShoppingListError('Failed to load shopping list.');
        } finally {
            setIsShoppingListLoading(false);
        }
    };

    const handleReplaceWithSpecific = async (recipe: Recipe, index: number) => {
        setAcceptError(null);
        try {
            const oldRecipe = mealPlan.recipes[index];

            // Record rejection of old recipe
            await recordFeedback(mealPlan.id, oldRecipe.id, 'rejected');

            // Auto-accept the chosen replacement — guard against re-firing if already accepted
            if (!acceptedRecipeIds.has(recipe.id)) {
                await recordFeedback(mealPlan.id, recipe.id, 'accepted');
            }

            const updatedRecipes = [...mealPlan.recipes];
            updatedRecipes[index] = recipe;

            // Update accepted set: remove old recipe, add new recipe
            setAcceptedRecipeIds(prev => {
                const next = new Set(prev);
                next.delete(oldRecipe.id);
                next.add(recipe.id);
                return next;
            });

            onMealPlanUpdated({
                ...mealPlan,
                recipes: updatedRecipes,
            });

            setReplacingIndex(null);
        } catch (error) {
            setAcceptError('Failed to replace recipe. Please try again.');
            console.error('Failed to replace recipe:', error);
        }
    };

    return (
        <div className="space-y-6">
            <div className="meal-plan-main-header flex justify-between items-center">
                <div>
                    <h2 className="text-2xl font-bold text-gray-900">
                        Your {mealPlan.recipes.length}-Day Meal Plan
                    </h2>
                    <p className="text-sm text-gray-600 mt-1">
                        {acceptedCount} of {totalCount} accepted
                    </p>
                    {acceptError && (
                        <p className="text-sm text-red-600 mt-1" role="alert">
                            {acceptError}
                        </p>
                    )}
                </div>
                <div className="flex gap-2">
                    <button
                        onClick={handleViewShoppingList}
                        disabled={acceptedCount === 0}
                        className="px-4 py-2 border border-gray-300 rounded-md text-gray-700 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        {acceptedCount > 0
                            ? `View Shopping List (${acceptedCount})`
                            : 'View Shopping List'}
                    </button>
                    <button
                        onClick={onReset}
                        className="px-4 py-2 border border-gray-300 rounded-md text-gray-700 hover:bg-gray-50"
                    >
                        Start Over
                    </button>
                </div>
            </div>

            <div className="recipe-grid grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
                {mealPlan.recipes.map((recipe, index) => (
                    <div key={`${recipe.id}-${index}`} className="space-y-2">
                        <div className="flex items-center justify-between mb-2">
                            <span className="text-sm font-medium text-gray-600">
                                Day {index + 1}
                            </span>
                            <button
                                onClick={() => setReplacingIndex(index)}
                                className="text-xs text-blue-600 hover:text-blue-800"
                            >
                                Choose Different
                            </button>
                        </div>

                        {replacingIndex === index ? (
                            <RecipeSelector
                                mealPlanId={mealPlan.id}
                                onSelect={(recipe) => handleReplaceWithSpecific(recipe, index)}
                                onCancel={() => setReplacingIndex(null)}
                            />
                        ) : (
                            <RecipeCard
                                recipe={recipe}
                                isAccepted={acceptedRecipeIds.has(recipe.id)}
                                onAccept={() => handleAccept(recipe)}
                                onReject={() => handleReject(recipe, index)}
                            />
                        )}
                    </div>
                ))}
            </div>

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
    );
}
