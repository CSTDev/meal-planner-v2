import { createMealPlan } from '@/lib/api/mealPlans';

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
