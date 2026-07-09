'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { getPastMealPlans, PastMealPlan } from '@/lib/api/mealPlans';

function formatPlanDate(createdAt: string): string {
    return new Date(createdAt).toLocaleDateString('en-GB', {
        day: 'numeric',
        month: 'long',
        year: 'numeric',
    });
}

export default function PastPlansPage() {
    const [plans, setPlans] = useState<PastMealPlan[] | null>(null);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        getPastMealPlans()
            .then(setPlans)
            .catch((err) => {
                console.error('Failed to load past plans:', err);
                setError('Failed to load past plans.');
            });
    }, []);

    return (
        <div className="py-12 px-4 sm:px-6 lg:px-8">
            <div className="max-w-3xl mx-auto">
                <div className="text-center mb-8">
                    <h1 className="text-3xl font-bold text-gray-900">Past Plans</h1>
                    <p className="mt-2 text-sm text-gray-600">
                        Browse your previously created meal plans
                    </p>
                </div>

                {error && (
                    <p role="alert" className="text-red-600 text-center">
                        {error}
                    </p>
                )}

                {!plans && !error && (
                    <p className="text-gray-600 text-center">Loading past plans...</p>
                )}

                {plans && plans.length === 0 && (
                    <p className="text-gray-600 text-center">
                        You haven&apos;t created any meal plans with recipes yet.
                    </p>
                )}

                {plans && plans.length > 0 && (
                    <ul className="bg-white rounded-lg border border-gray-200 divide-y divide-gray-200">
                        {plans.map((plan) => (
                            <li key={plan.id}>
                                <Link
                                    href={`/past-plans/${plan.id}`}
                                    className="flex items-center justify-between px-6 py-4 hover:bg-gray-50 transition-colors"
                                >
                                    <span className="font-medium text-gray-900">
                                        {formatPlanDate(plan.createdAt)}
                                    </span>
                                    <span className="text-sm text-gray-600">
                                        {plan.acceptedRecipeCount}{' '}
                                        {plan.acceptedRecipeCount === 1 ? 'recipe' : 'recipes'}
                                    </span>
                                </Link>
                            </li>
                        ))}
                    </ul>
                )}
            </div>
        </div>
    );
}
