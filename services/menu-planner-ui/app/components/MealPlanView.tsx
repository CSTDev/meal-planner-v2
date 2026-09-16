'use client';

import { useState } from 'react';
import RecipeCard from '@/app/components/RecipeCard';
import RecipeSelector from '@/app/components/RecipeSelector';
import ShoppingListOverlay from '@/app/components/ShoppingListOverlay';
import { recordFeedback, getShoppingList, addRecipeToMealPlan } from '@/lib/api/mealPlans';
import { MealPlan, Recipe, ShoppingListResponse } from '@/types/recipe';

interface MealPlanViewProps {
    mealPlan: MealPlan;
    /** Recipe ids already ACCEPTED when this view was hydrated (e.g. after a refresh). */
    initialAcceptedRecipeIds?: string[];
    /**
     * The number of days originally requested at generation time (carried
     * forward via a query param — never persisted server-side). Used only
     * to detect a shortfall on first mount; not kept in sync afterwards.
     */
    requestedCount?: number;
    onMealPlanUpdated: (mealPlan: MealPlan) => void;
    onReset: () => void;
}

export default function MealPlanView({
    mealPlan,
    initialAcceptedRecipeIds = [],
    requestedCount,
    onMealPlanUpdated,
    onReset
}: MealPlanViewProps) {
    const [replacingIndex, setReplacingIndex] = useState<number | null>(null);
    const [isShoppingListOpen, setIsShoppingListOpen] = useState(false);
    const [shoppingList, setShoppingList] = useState<ShoppingListResponse | null>(null);
    const [isShoppingListLoading, setIsShoppingListLoading] = useState(false);
    const [shoppingListError, setShoppingListError] = useState<string | null>(null);
    const [acceptedRecipeIds, setAcceptedRecipeIds] = useState<Set<string>>(
        () => new Set(initialAcceptedRecipeIds)
    );
    const [acceptError, setAcceptError] = useState<string | null>(null);
    // Trailing "+" tile: always available after the last RecipeCard, opens
    // the search-and-pick RecipeSelector to add a recipe straight to
    // ACCEPTED (not tied to any particular vacated slot).
    const [isAddingRecipe, setIsAddingRecipe] = useState(false);
    const [addRecipeError, setAddRecipeError] = useState<string | null>(null);
    // The number of slots this plan started with, before any reject
    // exhausted the pool and removed one. Used to render "X of Y filled".
    const [originalSlotCount] = useState(mealPlan.recipes.length);
    const [exhaustedCount, setExhaustedCount] = useState(0);
    // Shortfall banner (situation 1: short at creation): shown once on
    // first mount if fewer recipes were claimed than requested, dismissible
    // and never re-shown after that — it doesn't track further changes.
    const [isShortfallBannerDismissed, setIsShortfallBannerDismissed] = useState(false);
    const showShortfallBanner =
        !isShortfallBannerDismissed &&
        requestedCount !== undefined &&
        originalSlotCount < requestedCount;

    const acceptedCount = acceptedRecipeIds.size;
    const totalCount = mealPlan.recipes.length;
    const isEmpty = totalCount === 0;

    const handleReject = async (recipe: Recipe, index: number) => {
        setAcceptError(null);
        try {
            const { recipe: replacement } = await recordFeedback(mealPlan.id, recipe.id, 'rejected');

            // Remove from accepted set if it was accepted
            setAcceptedRecipeIds(prev => {
                const next = new Set(prev);
                next.delete(recipe.id);
                return next;
            });

            const updatedRecipes = [...mealPlan.recipes];
            if (replacement) {
                updatedRecipes[index] = replacement;
            } else {
                // Pool exhausted — no stable position to pin a placeholder to,
                // so the slot is simply removed.
                updatedRecipes.splice(index, 1);
                setExhaustedCount(prev => prev + 1);
            }

            onMealPlanUpdated({
                ...mealPlan,
                recipes: updatedRecipes,
            });
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

            // Atomic reject-old + accept-new in a single call.
            await recordFeedback(mealPlan.id, oldRecipe.id, 'rejected', recipe.id);

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

    const handleAddRecipe = async (recipe: Recipe) => {
        setAddRecipeError(null);
        try {
            await addRecipeToMealPlan(mealPlan.id, recipe.id);

            setAcceptedRecipeIds(prev => new Set([...prev, recipe.id]));

            onMealPlanUpdated({
                ...mealPlan,
                recipes: [...mealPlan.recipes, recipe],
            });

            setIsAddingRecipe(false);
        } catch (error) {
            setAddRecipeError('Failed to add recipe. Please try again.');
            console.error('Failed to add recipe:', error);
        }
    };

    return (
        <div className="space-y-6">
            <div className="meal-plan-main-header flex justify-between items-center">
                <div>
                    {isEmpty ? (
                        <>
                            <h2 className="text-2xl font-bold text-gray-900">
                                No Recipes in This Plan
                            </h2>
                            <p className="text-sm text-gray-600 mt-1">
                                This meal plan doesn&apos;t have any recipes yet. Start over to
                                generate a new one.
                            </p>
                        </>
                    ) : (
                        <>
                            <h2 className="text-2xl font-bold text-gray-900">
                                Your {mealPlan.recipes.length}-Day Meal Plan
                            </h2>
                            <p className="text-sm text-gray-600 mt-1">
                                {acceptedCount} of {totalCount} accepted
                            </p>
                        </>
                    )}
                    {exhaustedCount > 0 && (
                        <p className="text-sm text-amber-600 mt-1" role="status">
                            {totalCount} of {originalSlotCount} recipes could be filled — ran out of
                            available recipes for the rest.
                        </p>
                    )}
                    {showShortfallBanner && (
                        <div
                            className="flex items-center justify-between gap-4 text-sm text-amber-600 mt-1"
                            role="status"
                        >
                            <span>
                                Only {originalSlotCount} of the {requestedCount} you asked for were
                                available — add one below.
                            </span>
                            <button
                                onClick={() => setIsShortfallBannerDismissed(true)}
                                aria-label="Dismiss"
                                className="text-amber-600 hover:text-amber-800 font-medium"
                            >
                                Dismiss
                            </button>
                        </div>
                    )}
                    {acceptError && (
                        <p className="text-sm text-red-600 mt-1" role="alert">
                            {acceptError}
                        </p>
                    )}
                    {addRecipeError && (
                        <p className="text-sm text-red-600 mt-1" role="alert">
                            {addRecipeError}
                        </p>
                    )}
                </div>
                <div className="flex gap-2">
                    {!isEmpty && (
                        <button
                            onClick={handleViewShoppingList}
                            disabled={acceptedCount === 0}
                            className="px-4 py-2 border border-gray-300 rounded-md text-gray-700 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {acceptedCount > 0
                                ? `View Shopping List (${acceptedCount})`
                                : 'View Shopping List'}
                        </button>
                    )}
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

                <div className="space-y-2">
                    <div className="flex items-center justify-between mb-2 invisible">
                        <span className="text-sm font-medium">&nbsp;</span>
                    </div>
                    {isAddingRecipe ? (
                        <RecipeSelector
                            mealPlanId={mealPlan.id}
                            onSelect={handleAddRecipe}
                            onCancel={() => {
                                setIsAddingRecipe(false);
                                setAddRecipeError(null);
                            }}
                        />
                    ) : (
                        <button
                            onClick={() => setIsAddingRecipe(true)}
                            aria-label="Add a recipe"
                            className="w-full h-full min-h-[16rem] flex items-center justify-center border-2 border-dashed border-gray-300 rounded-lg text-gray-400 hover:text-blue-600 hover:border-blue-400 transition"
                        >
                            <span className="text-4xl font-light">+</span>
                        </button>
                    )}
                </div>
            </div>

            <ShoppingListOverlay
                isOpen={isShoppingListOpen}
                onClose={() => setIsShoppingListOpen(false)}
                isLoading={isShoppingListLoading}
                error={shoppingListError}
                shoppingList={shoppingList}
            />
        </div>
    );
}
