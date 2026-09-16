import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
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
        // Carries the requested numDays forward as a query param, so the
        // plan page can detect and surface a shortfall on first mount.
        expect(mockPush).toHaveBeenCalledWith('/meal-plan/plan-42?requested=7');
    });

    it('carries the user-entered numDays forward in the redirect query param', async () => {
        const user = userEvent.setup();
        (mealPlansApi.createMealPlan as jest.Mock).mockResolvedValue({
            id: 'plan-99',
            userId: 'user-1',
            recipeSource: 'own',
            createdAt: '2026-01-01',
            status: 'ACTIVE',
        });

        render(<MealPlanGenerator />);

        const numDaysInput = screen.getByLabelText(/number of days/i);
        fireEvent.change(numDaysInput, { target: { value: '10' } });

        await user.click(screen.getByRole('button', { name: /generate meal plan/i }));

        expect(mealPlansApi.createMealPlan).toHaveBeenCalledWith(10, 'own');
        expect(mockPush).toHaveBeenCalledWith('/meal-plan/plan-99?requested=10');
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
