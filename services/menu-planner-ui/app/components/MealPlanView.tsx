'use client';

import { useState } from 'react';
import RecipeCard from '@/app/components/RecipeCard';
import RecipeSelector from '@/app/components/RecipeSelector';
import { recordFeedback, getMealPlanRecommendations } from '@/lib/api/mealPlans';
import { MealPlan, Recipe } from '@/types/recipe';

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

    const handleReject = async (recipe: Recipe, index: number) => {
        try {
            // Record rejection
            await recordFeedback(mealPlan.id, recipe.id, 'rejected');

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
        try {
            await recordFeedback(mealPlan.id, recipe.id, 'accepted');
            // Visual feedback could be added here
        } catch (error) {
            console.error('Failed to accept recipe:', error);
        }
    };

    const handleReplaceWithSpecific = async (recipe: Recipe, index: number) => {
        try {
            // Record rejection of old recipe
            await recordFeedback(mealPlan.id, mealPlan.recipes[index].id, 'rejected');

            // Record acceptance of new recipe
            await recordFeedback(mealPlan.id, recipe.id, 'accepted');

            const updatedRecipes = [...mealPlan.recipes];
            updatedRecipes[index] = recipe;

            onMealPlanUpdated({
                ...mealPlan,
                recipes: updatedRecipes,
            });

            setReplacingIndex(null);
        } catch (error) {
            console.error('Failed to replace recipe:', error);
        }
    };

    return (
        <div className="space-y-6">
            <div className="flex justify-between items-center">
                <div>
                    <h2 className="text-2xl font-bold text-gray-900">
                        Your {mealPlan.recipes.length}-Day Meal Plan
                    </h2>
                    <p className="text-sm text-gray-600 mt-1">
                        Click ✓ to accept or ✗ to get a different recipe
                    </p>
                </div>
                <button
                    onClick={onReset}
                    className="px-4 py-2 border border-gray-300 rounded-md text-gray-700 hover:bg-gray-50"
                >
                    Start Over
                </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
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
                                onAccept={() => handleAccept(recipe)}
                                onReject={() => handleReject(recipe, index)}
                            />
                        )}
                    </div>
                ))}
            </div>
        </div>
    );
}