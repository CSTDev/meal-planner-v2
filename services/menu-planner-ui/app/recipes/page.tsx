'use client';

import { useEffect, useState } from 'react';
import RecipeCard from '@/app/components/RecipeCard';
import { Recipe } from '@/types/recipe';

interface RecipeApiResponse {
    id: string;
    title: string;
    description?: string;
    imageUrl?: string;
    prepTimeMinutes?: number;
    cookTimeMinutes?: number;
    servings?: number;
    ingredients: { name: string; quantity?: number; unit?: string }[];
    instructions: string[];
    tags?: string[];
    url?: string;
}

function toRecipe(r: RecipeApiResponse): Recipe {
    return {
        id: r.id,
        title: r.title,
        description: r.description,
        imageUrl: r.imageUrl,
        prepTime: r.prepTimeMinutes,
        cookTime: r.cookTimeMinutes,
        servings: r.servings,
        ingredients: (r.ingredients ?? []).map(i => ({
            name: i.name,
            quantity: i.quantity,
            unit: i.unit,
            originalText: i.name,
        })),
        instructionsList: r.instructions ?? [],
        canonicalUrl: r.url ?? '',
        host: r.url ? new URL(r.url).hostname : '',
    };
}

export default function RecipesPage() {
    const [recipes, setRecipes] = useState<Recipe[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        fetch('/api/recipes')
            .then(res => {
                if (!res.ok) throw new Error('Failed to load recipes');
                return res.json();
            })
            .then((data: RecipeApiResponse[]) => setRecipes(data.map(toRecipe)))
            .catch(err => setError(err.message))
            .finally(() => setIsLoading(false));
    }, []);

    return (
        <div className="py-12 px-4 sm:px-6 lg:px-8">
            <div className="max-w-7xl mx-auto">
                <div className="mb-8">
                    <h1 className="text-3xl font-bold text-gray-900">My Recipes</h1>
                    <p className="mt-2 text-sm text-gray-600">
                        Recipes you have added to your collection
                    </p>
                </div>

                {isLoading && (
                    <div className="text-center py-12 text-gray-500">Loading recipes...</div>
                )}

                {error && (
                    <div className="p-4 bg-red-50 border border-red-200 rounded-md">
                        <p className="text-sm text-red-800">{error}</p>
                    </div>
                )}

                {!isLoading && !error && recipes.length === 0 && (
                    <div className="text-center py-12 text-gray-500">
                        <p className="text-lg">No recipes yet.</p>
                        <p className="mt-2 text-sm">
                            Add recipes using the{' '}
                            <a href="/scrape" className="text-blue-600 hover:underline">
                                Add Recipe
                            </a>{' '}
                            page.
                        </p>
                    </div>
                )}

                {!isLoading && recipes.length > 0 && (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
                        {recipes.map(recipe => (
                            <RecipeCard
                                key={recipe.id}
                                recipe={recipe}
                                showActions={false}
                            />
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}
