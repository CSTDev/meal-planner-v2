import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ShoppingList from '@/app/components/ShoppingList';
import { ShoppingListResponse } from '@/types/recipe';

describe('ShoppingList', () => {
    it('renders a line per ingredient name with the aggregated total', () => {
        const data: ShoppingListResponse = {
            ingredients: [
                {
                    name: 'chicken breast',
                    amounts: [{ quantity: 680, unit: 'g' }],
                    breakdown: [
                        { recipeId: '1', recipeTitle: 'Recipe A', quantity: 340, unit: 'g' },
                        { recipeId: '2', recipeTitle: 'Recipe B', quantity: 340, unit: 'g' },
                    ],
                },
                {
                    name: 'garlic',
                    amounts: [{ quantity: 4, unit: 'clove' }],
                    breakdown: [
                        { recipeId: '1', recipeTitle: 'Recipe A', quantity: 4, unit: 'clove' },
                    ],
                },
            ],
        };

        render(<ShoppingList data={data} />);

        expect(screen.getByText(/chicken breast/i)).toBeInTheDocument();
        expect(screen.getByText(/680/)).toBeInTheDocument();
        expect(screen.getByText(/garlic/i)).toBeInTheDocument();
        expect(screen.getByText(/4/)).toBeInTheDocument();
    });

    it('hides the breakdown by default and shows it after expanding', async () => {
        const user = userEvent.setup();
        const data: ShoppingListResponse = {
            ingredients: [
                {
                    name: 'chicken breast',
                    amounts: [{ quantity: 680, unit: 'g' }],
                    breakdown: [
                        { recipeId: '1', recipeTitle: 'Recipe A', quantity: 340, unit: 'g' },
                        { recipeId: '2', recipeTitle: 'Recipe B', quantity: 340, unit: 'g' },
                    ],
                },
            ],
        };

        render(<ShoppingList data={data} />);

        expect(screen.queryByText('Recipe A')).not.toBeInTheDocument();
        expect(screen.queryByText('Recipe B')).not.toBeInTheDocument();

        const expandButton = screen.getByRole('button', { name: /chicken breast/i });
        await user.click(expandButton);

        expect(screen.getByText('Recipe A')).toBeInTheDocument();
        expect(screen.getByText('Recipe B')).toBeInTheDocument();
    });

    it('shows both amounts for an incompatible-unit ingredient without a merged total', () => {
        const data: ShoppingListResponse = {
            ingredients: [
                {
                    name: 'flour',
                    amounts: [
                        { quantity: 1, unit: 'cup' },
                        { quantity: 200, unit: 'g' },
                    ],
                    breakdown: [
                        { recipeId: '1', recipeTitle: 'Recipe A', quantity: 1, unit: 'cup' },
                        { recipeId: '2', recipeTitle: 'Recipe B', quantity: 200, unit: 'g' },
                    ],
                },
            ],
        };

        render(<ShoppingList data={data} />);

        expect(screen.getByText(/1 cup/i)).toBeInTheDocument();
        expect(screen.getByText(/200 g/i)).toBeInTheDocument();
    });

    it('renders an empty state when there are no accepted recipes', () => {
        const data: ShoppingListResponse = { ingredients: [] };

        render(<ShoppingList data={data} />);

        expect(screen.getByText(/no ingredients/i)).toBeInTheDocument();
    });

    it('renders quantity-less ingredients without a numeric amount', () => {
        const data: ShoppingListResponse = {
            ingredients: [
                {
                    name: 'salt',
                    amounts: [],
                    breakdown: [
                        { recipeId: '1', recipeTitle: 'Recipe A', quantity: null, unit: null },
                    ],
                },
            ],
        };

        render(<ShoppingList data={data} />);

        expect(screen.getByText(/salt/i)).toBeInTheDocument();
    });
});
