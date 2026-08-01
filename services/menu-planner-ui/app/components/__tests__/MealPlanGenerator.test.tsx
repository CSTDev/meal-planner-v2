import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import MealPlanGenerator from '@/app/components/MealPlanGenerator';
import * as mealPlansApi from '@/lib/api/mealPlans';

const mockPush = jest.fn();

jest.mock('next/navigation', () => ({
    useRouter: jest.fn(() => ({ push: mockPush })),
}));

jest.mock('@/lib/api/mealPlans');

describe('MealPlanGenerator', () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('creates a meal plan and redirects to /meal-plan/[id]', async () => {
        const user = userEvent.setup();
        (mealPlansApi.createMealPlan as jest.Mock).mockResolvedValue({
            id: 'plan-42',
            userId: 'user-1',
            recipeSource: 'own',
            createdAt: '2026-01-01',
            status: 'ACTIVE',
        });

        render(<MealPlanGenerator />);

        await user.click(screen.getByRole('button', { name: /generate meal plan/i }));

        expect(mealPlansApi.createMealPlan).toHaveBeenCalledTimes(1);
        expect(mockPush).toHaveBeenCalledWith('/meal-plan/plan-42');
    });

    it('does not redirect and shows an error when creation fails', async () => {
        const user = userEvent.setup();
        (mealPlansApi.createMealPlan as jest.Mock).mockRejectedValue(new Error('Failed to create meal plan'));

        render(<MealPlanGenerator />);

        await user.click(screen.getByRole('button', { name: /generate meal plan/i }));

        expect(await screen.findByText(/failed to create meal plan/i)).toBeInTheDocument();
        expect(mockPush).not.toHaveBeenCalled();
    });
});
