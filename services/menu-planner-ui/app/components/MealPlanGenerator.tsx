'use client';

import { useState } from 'react';
import { createMealPlan, getMealPlanRecommendations } from '@/lib/api/mealPlans';
import { MealPlan } from '@/types/recipe';

interface MealPlanGeneratorProps {
    onMealPlanCreated: (mealPlan: MealPlan) => void;
}

export default function MealPlanGenerator({ onMealPlanCreated }: MealPlanGeneratorProps) {
    const [numDays, setNumDays] = useState(7);
    const [recipeSource, setRecipeSource] = useState<'own' | 'all' | 'shared'>('own');
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleGenerate = async () => {
        setIsLoading(true);
        setError(null);

        try {
            // Create meal plan
            const newMealPlan = await createMealPlan(numDays, recipeSource);

            // Get recommendations
            const recommendations = await getMealPlanRecommendations(
                newMealPlan.id,
                numDays
            );

            onMealPlanCreated({
                ...newMealPlan,
                recipes: recommendations,
            });
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to generate meal plan');
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="max-w-2xl mx-auto bg-white shadow-md rounded-lg p-8">
            <div className="space-y-6">
                <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                        Number of Days
                    </label>
                    <input
                        type="number"
                        min="1"
                        max="30"
                        value={numDays}
                        onChange={(e) => setNumDays(parseInt(e.target.value) || 1)}
                        className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none"
                    />
                </div>

                <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                        Recipe Source
                    </label>
                    <select
                        value={recipeSource}
                        onChange={(e) => setRecipeSource(e.target.value as 'own' | 'all' | 'shared')}
                        className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none"
                    >
                        <option value="own">My Recipes Only</option>
                        <option value="all">All Recipes</option>
                        <option value="shared">Community Recipes</option>
                    </select>
                </div>

                {error && (
                    <div className="p-4 bg-red-50 border border-red-200 rounded-md">
                        <p className="text-sm text-red-800">{error}</p>
                    </div>
                )}

                <button
                    onClick={handleGenerate}
                    disabled={isLoading}
                    className={`w-full py-3 px-4 rounded-md font-medium text-white transition ${isLoading
                        ? 'bg-gray-400 cursor-not-allowed'
                        : 'bg-blue-600 hover:bg-blue-700'
                        }`}
                >
                    {isLoading ? 'Generating...' : 'Generate Meal Plan'}
                </button>
            </div>
        </div>
    );
}