import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import MealPlanView from '@/app/components/MealPlanView';
import { MealPlan } from '@/types/recipe';
import * as mealPlansApi from '@/lib/api/mealPlans';

jest.mock('@/lib/api/mealPlans');

const mockMealPlan: MealPlan = {
    id: 'plan-1',
    userId: 'user-1',
    recipeSource: 'all',
    createdAt: '2026-01-01',
    status: 'ACTIVE',
    recipes: [
        {
            id: 'recipe-1',
            title: 'Pancakes',
            ingredients: [],
            instructionsList: [],
            canonicalUrl: 'https://example.com/pancakes',
            host: 'example.com',
        },
    ],
};

describe('MealPlanView shopping list integration', () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('fetches and displays the shopping list when the button is clicked', async () => {
        const user = userEvent.setup();
        (mealPlansApi.getShoppingList as jest.Mock).mockResolvedValue({
            ingredients: [
                {
                    name: 'chicken breast',
                    amounts: [{ quantity: 680, unit: 'g' }],
                    breakdown: [
                        { recipeId: 'recipe-1', recipeTitle: 'Pancakes', quantity: 680, unit: 'g' },
                    ],
                },
            ],
        });

        render(
            <MealPlanView
                mealPlan={mockMealPlan}
                onMealPlanUpdated={jest.fn()}
                onReset={jest.fn()}
            />
        );

        await user.click(screen.getByRole('button', { name: /view shopping list/i }));

        expect(mealPlansApi.getShoppingList).toHaveBeenCalledWith('plan-1');

        await waitFor(() => {
            expect(screen.getByText(/chicken breast/i)).toBeInTheDocument();
        });
    });
});
