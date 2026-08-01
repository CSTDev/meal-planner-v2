import React from 'react';
import { render, screen } from '@testing-library/react';
import MealPlanPage from '@/app/meal-plan/page';

jest.mock('@/app/components/MealPlanGenerator', () => {
    const MockMealPlanGenerator = () => <div data-testid="meal-plan-generator" />;
    MockMealPlanGenerator.displayName = 'MockMealPlanGenerator';
    return MockMealPlanGenerator;
});

describe('MealPlanPage', () => {
    it('renders the page heading', () => {
        render(<MealPlanPage />);
        expect(screen.getByRole('heading', { name: /meal plan generator/i })).toBeInTheDocument();
    });

    it('page header block has meal-plan-page-header class for print targeting', () => {
        const { container } = render(<MealPlanPage />);
        const headerBlock = container.querySelector('.meal-plan-page-header');
        expect(headerBlock).toBeInTheDocument();
        expect(headerBlock).toContainElement(screen.getByRole('heading', { name: /meal plan generator/i }));
    });

    it('renders the meal plan generator, which owns creation and redirect', () => {
        render(<MealPlanPage />);
        expect(screen.getByTestId('meal-plan-generator')).toBeInTheDocument();
    });
});
