import { createMealPlan, addRecipeToMealPlan } from '@/lib/api/mealPlans';

describe('createMealPlan', () => {
    beforeEach(() => {
        global.fetch = jest.fn().mockResolvedValue({
            ok: true,
            json: async () => ({
                id: 'plan-1',
                userId: 'user-1',
                recipeSource: 'all',
                createdAt: '2026-01-01',
                status: 'ACTIVE',
            }),
        }) as jest.Mock;
    });

    it('sends the request body using camelCase keys matching the backend DTO', async () => {
        await createMealPlan(3, 'all');

        expect(global.fetch).toHaveBeenCalledWith(
            '/api/meal-plans',
            expect.objectContaining({
                method: 'POST',
                body: JSON.stringify({ numRecipes: 3, recipeSource: 'all' }),
            })
        );
    });
});

describe('addRecipeToMealPlan', () => {
    it('POSTs the recipeId and returns the added recipe', async () => {
        global.fetch = jest.fn().mockResolvedValue({
            ok: true,
            json: async () => ({ id: 'recipe-9', title: 'Chili' }),
        }) as jest.Mock;

        const result = await addRecipeToMealPlan('plan-1', 'recipe-9');

        expect(global.fetch).toHaveBeenCalledWith(
            '/api/meal-plans/plan-1/recipes',
            expect.objectContaining({
                method: 'POST',
                body: JSON.stringify({ recipeId: 'recipe-9' }),
            })
        );
        expect(result).toEqual({ id: 'recipe-9', title: 'Chili' });
    });

    it('throws when the request fails', async () => {
        global.fetch = jest.fn().mockResolvedValue({ ok: false }) as jest.Mock;

        await expect(addRecipeToMealPlan('plan-1', 'recipe-9')).rejects.toThrow();
    });
});
