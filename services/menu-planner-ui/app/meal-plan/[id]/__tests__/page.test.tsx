import React from 'react';
import { render, screen } from '@testing-library/react';
import MealPlanDetailPage from '@/app/meal-plan/[id]/page';

jest.mock('next/navigation', () => ({
    useParams: jest.fn(() => ({ id: 'plan-1' })),
    useRouter: jest.fn(() => ({ push: jest.fn() })),
}));

jest.mock('@/lib/api/mealPlans', () => ({
    getMealPlan: jest.fn(),
}));

jest.mock('@/app/components/MealPlanView', () => {
    const MockMealPlanView = (props: { mealPlan: { recipes: { id: string }[] }; initialAcceptedRecipeIds: string[] }) => (
        <div data-testid="meal-plan-view">
            <span data-testid="recipe-count">{props.mealPlan.recipes.length}</span>
            <span data-testid="accepted-count">{props.initialAcceptedRecipeIds.length}</span>
        </div>
    );
    MockMealPlanView.displayName = 'MockMealPlanView';
    return MockMealPlanView;
});

import { getMealPlan } from '@/lib/api/mealPlans';

const mockState = {
    id: 'plan-1',
    userId: 'user-1',
    recipeSource: 'all',
    createdAt: '2026-01-01',
    status: 'ACTIVE',
    recipes: [
        { recipe: { id: 'recipe-1', title: 'Pancakes' }, status: 'ACCEPTED' },
        { recipe: { id: 'recipe-2', title: 'Waffles' }, status: 'OFFERED' },
    ],
};

describe('MealPlanDetailPage', () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('fetches the plan state for the id in the URL on mount', async () => {
        (getMealPlan as jest.Mock).mockResolvedValue(mockState);
        render(<MealPlanDetailPage />);

        await screen.findByTestId('meal-plan-view');
        expect(getMealPlan).toHaveBeenCalledWith('plan-1');
    });

    it('passes the fetched recipes and accepted ids down to MealPlanView', async () => {
        (getMealPlan as jest.Mock).mockResolvedValue(mockState);
        render(<MealPlanDetailPage />);

        await screen.findByTestId('meal-plan-view');
        expect(screen.getByTestId('recipe-count')).toHaveTextContent('2');
        expect(screen.getByTestId('accepted-count')).toHaveTextContent('1');
    });

    it('shows a loading state before the fetch resolves', () => {
        (getMealPlan as jest.Mock).mockReturnValue(new Promise(() => {}));
        render(<MealPlanDetailPage />);

        expect(screen.getByText(/loading/i)).toBeInTheDocument();
    });

    it('shows an error message when the fetch fails', async () => {
        (getMealPlan as jest.Mock).mockRejectedValue(new Error('boom'));
        render(<MealPlanDetailPage />);

        expect(await screen.findByRole('alert')).toHaveTextContent(/failed to load/i);
    });
});
