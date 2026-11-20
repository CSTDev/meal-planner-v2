import { createMealPlan, addRecipeToMealPlan, deleteMealPlan } from '@/lib/api/mealPlans';

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

describe('deleteMealPlan', () => {
    it('sends a DELETE to the plan URL and resolves on success', async () => {
        global.fetch = jest.fn().mockResolvedValue({ ok: true, status: 204 }) as jest.Mock;

        await expect(deleteMealPlan('plan-1')).resolves.toBeUndefined();

        expect(global.fetch).toHaveBeenCalledWith(
            '/api/meal-plans/plan-1',
            expect.objectContaining({ method: 'DELETE' })
        );
    });

    it('treats a 404 (plan already gone) as success', async () => {
        global.fetch = jest.fn().mockResolvedValue({ ok: false, status: 404 }) as jest.Mock;

        await expect(deleteMealPlan('plan-1')).resolves.toBeUndefined();
    });

    it('throws on a server error', async () => {
        global.fetch = jest.fn().mockResolvedValue({ ok: false, status: 500 }) as jest.Mock;

        await expect(deleteMealPlan('plan-1')).rejects.toThrow('Failed to delete meal plan');
    });

    it('propagates a network failure', async () => {
        global.fetch = jest.fn().mockRejectedValue(new TypeError('network down')) as jest.Mock;

        await expect(deleteMealPlan('plan-1')).rejects.toThrow('network down');
    });
});
