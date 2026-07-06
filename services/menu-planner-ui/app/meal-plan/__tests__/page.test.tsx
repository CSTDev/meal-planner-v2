import React from 'react';
import { render, screen } from '@testing-library/react';
import MealPlanPage from '@/app/meal-plan/page';

jest.mock('@/app/components/MealPlanGenerator', () => {
    const MockMealPlanGenerator = ({ onMealPlanCreated }: { onMealPlanCreated: (plan: unknown) => void }) => (
        <div data-testid="meal-plan-generator" onClick={() => onMealPlanCreated(null)} />
    );
    MockMealPlanGenerator.displayName = 'MockMealPlanGenerator';
    return MockMealPlanGenerator;
});

jest.mock('@/app/components/MealPlanView', () => {
    const MockMealPlanView = () => <div data-testid="meal-plan-view" />;
    MockMealPlanView.displayName = 'MockMealPlanView';
    return MockMealPlanView;
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
});
