const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:3000';

export interface CreateMealPlanRequest {
    numRecipes: number;
    recipeSource: 'own' | 'all' | 'shared';
}

export interface MealPlanResponse {
    id: string;
    userId: string;
    recipeSource: 'own' | 'all' | 'shared';
    createdAt: string;
    status: string;
}

export interface RecordFeedbackRequest {
    recipeId: string;
    action: 'accepted' | 'rejected';
}

/**
 * Create a new meal plan
 */
export async function createMealPlan(
    numDays: number,
    recipeSource: 'own' | 'all' | 'shared'
): Promise<MealPlanResponse> {
    const response = await fetch(`${API_BASE_URL}/api/meal-plans`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            num_recipes: numDays,
            recipe_source: recipeSource,
        }),
    });

    if (!response.ok) {
        throw new Error('Failed to create meal plan');
    }

    return response.json();
}

/**
 * Get recipe recommendations for a meal plan
 */
export async function getMealPlanRecommendations(
    mealPlanId: string,
    numRecipes: number
) {
    const response = await fetch(
        `${API_BASE_URL}/api/meal-plans/${mealPlanId}/recommendations?num_recipes=${numRecipes}`,
        {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
            },
        }
    );

    if (!response.ok) {
        throw new Error('Failed to get recommendations');
    }

    return response.json();
}

/**
 * Record user feedback (accept/reject) for a recipe
 */
export async function recordFeedback(
    mealPlanId: string,
    recipeId: string,
    action: 'accepted' | 'rejected'
): Promise<void> {
    const response = await fetch(
        `${API_BASE_URL}/api/meal-plans/${mealPlanId}/feedback`,
        {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                recipe_id: recipeId,
                action,
            }),
        }
    );

    if (!response.ok) {
        throw new Error('Failed to record feedback');
    }
}