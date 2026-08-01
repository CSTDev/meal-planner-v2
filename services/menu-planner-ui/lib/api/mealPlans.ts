import { Recipe, ShoppingListResponse } from '@/types/recipe';

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
 * One row of a meal plan's live state: a recipe currently occupying a
 * slot, either still pending a decision or already accepted.
 */
export interface MealPlanRecipeState {
    recipe: Recipe;
    status: 'OFFERED' | 'ACCEPTED';
}

/**
 * The plan's full current live state, as returned by
 * GET /api/meal-plans/{id} — this is the only place plan state is loaded
 * from, whether that's right after creation or after a refresh.
 */
export interface MealPlanFullState {
    id: string;
    userId: string;
    recipeSource: 'own' | 'all' | 'shared';
    createdAt: string;
    status: string;
    recipes: MealPlanRecipeState[];
}

/**
 * The result of submitting feedback for one slot. `recipe` is the recipe
 * now occupying the slot (unchanged on accept, the replacement on
 * reject/replace), or `null` when a reject exhausted the eligible pool and
 * the slot was removed entirely.
 */
export interface FeedbackResult {
    recipe: Recipe | null;
    status: string;
}

export interface PastMealPlan {
    id: string;
    createdAt: string;
    recipeSource: string;
    acceptedRecipeCount: number;
}

/**
 * A recipe as returned by the backend's accepted-recipes endpoint
 * (the backend RecipeDTO shape, which differs from the scraper-oriented
 * Recipe type in types/recipe.ts)
 */
export interface AcceptedRecipe {
    id: string;
    title: string;
    description?: string;
    url?: string;
    imageUrl?: string;
}

/**
 * Create a new meal plan
 */
export async function createMealPlan(
    numDays: number,
    recipeSource: 'own' | 'all' | 'shared'
): Promise<MealPlanResponse> {
    const response = await fetch(`/api/meal-plans`, {
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
 * Get the user's most recent meal plans (max 10, newest first,
 * plans with no accepted recipes excluded)
 */
export async function getPastMealPlans(): Promise<PastMealPlan[]> {
    const response = await fetch(`/api/meal-plans`, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
        },
    });

    if (!response.ok) {
        throw new Error('Failed to get past meal plans');
    }

    return response.json();
}

/**
 * Get a meal plan's full current state (every recipe currently offered or
 * accepted, with its status). This is what the [id] page hydrates from on
 * mount, whether that's right after creation or after a refresh.
 */
export async function getMealPlan(mealPlanId: string): Promise<MealPlanFullState> {
    const response = await fetch(`/api/meal-plans/${mealPlanId}`, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
        },
    });

    if (!response.ok) {
        throw new Error('Failed to get meal plan');
    }

    return response.json();
}

/**
 * Record user feedback (accept/reject) for a recipe. On reject, optionally
 * pass `replacementRecipeId` to atomically replace it with a specific
 * recipe (the "Choose Different" flow) instead of claiming a random one.
 *
 * Returns the recipe now occupying the slot (`null` if a plain reject
 * exhausted the eligible pool and the slot was removed) plus its status.
 */
export async function recordFeedback(
    mealPlanId: string,
    recipeId: string,
    action: 'accepted' | 'rejected',
    replacementRecipeId?: string
): Promise<FeedbackResult> {
    const response = await fetch(
        `/api/meal-plans/${mealPlanId}/feedback`,
        {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                recipe_id: recipeId,
                action,
                ...(replacementRecipeId ? { replacement_recipe_id: replacementRecipeId } : {}),
            }),
        }
    );

    if (!response.ok) {
        throw new Error('Failed to record feedback');
    }

    return response.json();
}

/**
 * Get the recipes currently accepted into a meal plan,
 * most recently accepted first
 */
export async function getAcceptedRecipes(
    mealPlanId: string
): Promise<AcceptedRecipe[]> {
    const response = await fetch(
        `/api/meal-plans/${mealPlanId}/accepted-recipes`,
        {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
            },
        }
    );

    if (!response.ok) {
        throw new Error('Failed to get accepted recipes');
    }

    return response.json();
}

/**
 * Get the aggregated shopping list for a meal plan
 */
export async function getShoppingList(
    mealPlanId: string
): Promise<ShoppingListResponse> {
    const response = await fetch(
        `/api/meal-plans/${mealPlanId}/shopping-list`,
        {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
            },
        }
    );

    if (!response.ok) {
        throw new Error('Failed to get shopping list');
    }

    return response.json();
}