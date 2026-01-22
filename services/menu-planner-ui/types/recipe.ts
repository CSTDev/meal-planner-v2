export interface MealPlan {
    id: string;
    userId: string;
    recipeSource: 'own' | 'all' | 'shared';
    createdAt: string;
    status: string;
    recipes: Recipe[];
}

export interface Recipe {
    id: string;
    title: string;
    description?: string;
    ingredients: Ingredient[];
    instructionsList: string[];
    prepTime?: number;
    cookTime?: number;
    totalTime?: number;
    servings?: number;
    imageUrl?: string;
    canonicalUrl: string;
    host: string;
    ratings?: number;
    language?: string;
}

export interface Ingredient {
    name: string;
    quantity?: number;
    unit?: string;
    originalText: string;
}