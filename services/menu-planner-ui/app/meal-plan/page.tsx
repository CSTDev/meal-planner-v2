'use client';

import MealPlanGenerator from '@/app/components/MealPlanGenerator';

export default function MealPlanPage() {
    return (
        <div className="py-12 px-4 sm:px-6 lg:px-8">
            <div className="max-w-7xl mx-auto">
                <div className="text-center mb-8 meal-plan-page-header">
                    <h1 className="text-3xl font-bold text-gray-900">
                        Meal Plan Generator
                    </h1>
                    <p className="mt-2 text-sm text-gray-600">
                        Generate a personalized meal plan based on your preferences
                    </p>
                </div>

                <MealPlanGenerator />
            </div>
        </div>
    );
}
