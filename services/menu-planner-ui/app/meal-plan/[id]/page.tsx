'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import MealPlanView from '@/app/components/MealPlanView';
import { getMealPlan } from '@/lib/api/mealPlans';
import { MealPlan } from '@/types/recipe';

export default function MealPlanDetailPage() {
    const params = useParams<{ id: string }>();
    const router = useRouter();
    const planId = params.id;

    const [mealPlan, setMealPlan] = useState<MealPlan | null>(null);
    const [initialAcceptedRecipeIds, setInitialAcceptedRecipeIds] = useState<string[]>([]);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!planId) return;
        getMealPlan(planId)
            .then((state) => {
                setMealPlan({
                    id: state.id,
                    userId: state.userId,
                    recipeSource: state.recipeSource,
                    createdAt: state.createdAt,
                    status: state.status,
                    recipes: state.recipes.map((r) => r.recipe),
                });
                setInitialAcceptedRecipeIds(
                    state.recipes.filter((r) => r.status === 'ACCEPTED').map((r) => r.recipe.id)
                );
            })
            .catch((err) => {
                console.error('Failed to load meal plan:', err);
                setError('Failed to load this meal plan.');
            });
    }, [planId]);

    return (
        <div className="py-12 px-4 sm:px-6 lg:px-8">
            <div className="max-w-7xl mx-auto">
                {error && (
                    <p role="alert" className="text-red-600 text-center">
                        {error}
                    </p>
                )}

                {!mealPlan && !error && (
                    <p className="text-gray-600 text-center">Loading meal plan...</p>
                )}

                {mealPlan && (
                    <MealPlanView
                        mealPlan={mealPlan}
                        initialAcceptedRecipeIds={initialAcceptedRecipeIds}
                        onMealPlanUpdated={setMealPlan}
                        onReset={() => router.push('/meal-plan')}
                    />
                )}
            </div>
        </div>
    );
}
