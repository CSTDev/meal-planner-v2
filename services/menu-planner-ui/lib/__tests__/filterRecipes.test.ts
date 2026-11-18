import { filterRecipes } from '@/lib/filterRecipes';
import { Recipe } from '@/types/recipe';

function recipe(id: string, title: string, ingredients: string[]): Recipe {
    return {
        id,
        title,
        ingredients: ingredients.map(name => ({ name, originalText: name })),
        instructionsList: [],
        canonicalUrl: '',
        host: '',
    };
}

const recipes = [
    recipe('1', 'Chicken Curry', ['chicken thighs', 'Coconut Milk']),
    recipe('2', 'Tomato Soup', ['tomatoes', 'basil']),
    recipe('3', 'Basil Pesto Pasta', ['pasta', 'pine nuts', 'basil']),
];

describe('filterRecipes', () => {
    it('matches title case-insensitively', () => {
        expect(filterRecipes(recipes, 'cHiCkEn').map(r => r.id)).toEqual(['1']);
    });

    it('matches ingredient names', () => {
        expect(filterRecipes(recipes, 'coconut').map(r => r.id)).toEqual(['1']);
        expect(filterRecipes(recipes, 'basil').map(r => r.id)).toEqual(['2', '3']);
    });

    it('requires every word to match title or an ingredient, in any order', () => {
        expect(filterRecipes(recipes, 'pasta basil').map(r => r.id)).toEqual(['3']);
        expect(filterRecipes(recipes, 'basil pesto').map(r => r.id)).toEqual(['3']);
        expect(filterRecipes(recipes, 'soup pine')).toEqual([]);
    });

    it('returns the full list for blank or whitespace-only queries', () => {
        expect(filterRecipes(recipes, '')).toEqual(recipes);
        expect(filterRecipes(recipes, '   ')).toEqual(recipes);
    });

    it('returns an empty list when nothing matches', () => {
        expect(filterRecipes(recipes, 'xyz')).toEqual([]);
    });
});
