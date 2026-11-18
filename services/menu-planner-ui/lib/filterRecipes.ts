import { Recipe } from '@/types/recipe';

/**
 * Case-insensitive search over recipe title and ingredient names.
 * The query is split on whitespace; every word must match the title or
 * at least one ingredient name. A blank query returns all recipes.
 */
export function filterRecipes(recipes: Recipe[], query: string): Recipe[] {
    const words = query.toLowerCase().split(/\s+/).filter(Boolean);
    if (words.length === 0) return recipes;

    return recipes.filter(recipe => {
        const haystacks = [recipe.title, ...recipe.ingredients.map(i => i.name)].map(s =>
            (s ?? '').toLowerCase(),
        );
        return words.every(word => haystacks.some(h => h.includes(word)));
    });
}
