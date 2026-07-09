import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ShoppingListOverlay from '@/app/components/ShoppingListOverlay';
import { ShoppingListResponse } from '@/types/recipe';

const mockShoppingList: ShoppingListResponse = {
    ingredients: [
        {
            name: 'butter',
            amounts: [{ quantity: 100, unit: 'g' }],
            breakdown: [
                { recipeId: 'recipe-1', recipeTitle: 'Pancakes', quantity: 100, unit: 'g' },
            ],
        },
    ],
};

describe('ShoppingListOverlay', () => {
    beforeEach(() => {
        window.print = jest.fn();
        document.body.classList.remove('shopping-list-open');
    });

    it('renders nothing when closed', () => {
        const { container } = render(
            <ShoppingListOverlay
                isOpen={false}
                onClose={jest.fn()}
                isLoading={false}
                error={null}
                shoppingList={mockShoppingList}
            />
        );
        expect(container).toBeEmptyDOMElement();
    });

    it('renders the shopping list when open', () => {
        render(
            <ShoppingListOverlay
                isOpen={true}
                onClose={jest.fn()}
                isLoading={false}
                error={null}
                shoppingList={mockShoppingList}
            />
        );
        expect(screen.getByRole('heading', { name: /shopping list/i })).toBeInTheDocument();
        expect(screen.getByText('butter')).toBeInTheDocument();
    });

    it('adds shopping-list-open to the body while open and removes it when closed', () => {
        const { rerender } = render(
            <ShoppingListOverlay
                isOpen={true}
                onClose={jest.fn()}
                isLoading={false}
                error={null}
                shoppingList={mockShoppingList}
            />
        );
        expect(document.body.classList.contains('shopping-list-open')).toBe(true);

        rerender(
            <ShoppingListOverlay
                isOpen={false}
                onClose={jest.fn()}
                isLoading={false}
                error={null}
                shoppingList={mockShoppingList}
            />
        );
        expect(document.body.classList.contains('shopping-list-open')).toBe(false);
    });

    it('removes shopping-list-open from the body on unmount', () => {
        const { unmount } = render(
            <ShoppingListOverlay
                isOpen={true}
                onClose={jest.fn()}
                isLoading={false}
                error={null}
                shoppingList={mockShoppingList}
            />
        );
        expect(document.body.classList.contains('shopping-list-open')).toBe(true);

        unmount();
        expect(document.body.classList.contains('shopping-list-open')).toBe(false);
    });

    it('calls window.print when the print button is clicked', async () => {
        const user = userEvent.setup();
        render(
            <ShoppingListOverlay
                isOpen={true}
                onClose={jest.fn()}
                isLoading={false}
                error={null}
                shoppingList={mockShoppingList}
            />
        );

        await user.click(screen.getByRole('button', { name: /print shopping list/i }));
        expect(window.print).toHaveBeenCalledTimes(1);
    });

    it('calls onClose from the close button and the backdrop', async () => {
        const user = userEvent.setup();
        const onClose = jest.fn();
        const { container } = render(
            <ShoppingListOverlay
                isOpen={true}
                onClose={onClose}
                isLoading={false}
                error={null}
                shoppingList={mockShoppingList}
            />
        );

        await user.click(screen.getByRole('button', { name: /close shopping list/i }));
        expect(onClose).toHaveBeenCalledTimes(1);

        const backdrop = container.querySelector('.shopping-list-backdrop');
        expect(backdrop).not.toBeNull();
        await user.click(backdrop as Element);
        expect(onClose).toHaveBeenCalledTimes(2);
    });

    it('shows a loading message while loading', () => {
        render(
            <ShoppingListOverlay
                isOpen={true}
                onClose={jest.fn()}
                isLoading={true}
                error={null}
                shoppingList={null}
            />
        );
        expect(screen.getByText(/loading shopping list/i)).toBeInTheDocument();
    });

    it('shows an error message when loading failed', () => {
        render(
            <ShoppingListOverlay
                isOpen={true}
                onClose={jest.fn()}
                isLoading={false}
                error="Failed to load shopping list."
                shoppingList={null}
            />
        );
        expect(screen.getByText('Failed to load shopping list.')).toBeInTheDocument();
    });
});
