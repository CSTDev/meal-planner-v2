'use client';

import { useState } from 'react';
import MealPlanGenerator from '@/app/components/MealPlanGenerator';
import MealPlanView from '@/app/components/MealPlanView';
import { MealPlan } from '@/types/recipe';

export default function MealPlanPage() {
    const [mealPlan, setMealPlan] = useState<MealPlan | null>(null);

    const handleMealPlanCreated = (newMealPlan: MealPlan) => {
        setMealPlan(newMealPlan);
    };

    const handleMealPlanUpdated = (updatedMealPlan: MealPlan) => {
        setMealPlan(updatedMealPlan);
    };

    return (
        <div className="py-12 px-4 sm:px-6 lg:px-8">
            <div className="max-w-7xl mx-auto">
                <div className="text-center mb-8">
                    <h1 className="text-3xl font-bold text-gray-900">
                        Meal Plan Generator
                    </h1>
                    <p className="mt-2 text-sm text-gray-600">
                        Generate a personalized meal plan based on your preferences
                    </p>
                </div>

                {!mealPlan ? (
                    <MealPlanGenerator onMealPlanCreated={handleMealPlanCreated} />
                ) : (
                    <MealPlanView
                        mealPlan={mealPlan}
                        onMealPlanUpdated={handleMealPlanUpdated}
                        onReset={() => setMealPlan(null)}
                    />
                )}
            </div>
        </div>
    );
}