import React from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PastPlanDetailPage from '@/app/past-plans/[id]/page';

jest.mock('next/navigation', () => ({
    useParams: jest.fn(() => ({ id: 'plan-1' })),
}));

jest.mock('@/lib/api/mealPlans', () => ({
    getAcceptedRecipes: jest.fn(),
    getShoppingList: jest.fn(),
}));

jest.mock('next/link', () => {
    const MockLink = ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
        <a href={href} {...props}>{children}</a>
    );
    MockLink.displayName = 'MockLink';
    return MockLink;
});

import { getAcceptedRecipes, getShoppingList } from '@/lib/api/mealPlans';

const mockRecipes = [
    { id: 'recipe-3', title: 'French Toast' },
    { id: 'recipe-2', title: 'Waffles' },
    { id: 'recipe-1', title: 'Pancakes' },
];

const mockShoppingList = {
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

describe('PastPlanDetailPage', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        document.body.classList.remove('shopping-list-open');
        (getAcceptedRecipes as jest.Mock).mockResolvedValue(mockRecipes);
        (getShoppingList as jest.Mock).mockResolvedValue(mockShoppingList);
    });

    it('renders the accepted recipes in the order returned by the API', async () => {
        render(<PastPlanDetailPage />);

        const list = await screen.findByRole('list');
        const items = within(list).getAllByRole('listitem');
        expect(items).toHaveLength(3);
        expect(items[0]).toHaveTextContent('French Toast');
        expect(items[1]).toHaveTextContent('Waffles');
        expect(items[2]).toHaveTextContent('Pancakes');
    });

    it('fetches the accepted recipes for the plan in the URL', async () => {
        render(<PastPlanDetailPage />);
        await screen.findByText('French Toast');
        expect(getAcceptedRecipes).toHaveBeenCalledWith('plan-1');
    });

    it('opens the shopping list overlay when Generate shopping list is clicked', async () => {
        const user = userEvent.setup();
        render(<PastPlanDetailPage />);
        await screen.findByText('French Toast');

        await user.click(screen.getByRole('button', { name: /generate shopping list/i }));

        expect(getShoppingList).toHaveBeenCalledWith('plan-1');
        expect(await screen.findByText('butter')).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: /shopping list/i })).toBeInTheDocument();
    });

    it('closes the shopping list overlay via the close button', async () => {
        const user = userEvent.setup();
        render(<PastPlanDetailPage />);
        await screen.findByText('French Toast');

        await user.click(screen.getByRole('button', { name: /generate shopping list/i }));
        await screen.findByText('butter');

        await user.click(screen.getByRole('button', { name: /close shopping list/i }));
        expect(screen.queryByText('butter')).not.toBeInTheDocument();
    });

    it('sets shopping-list-open on the body while the overlay is open (print mode)', async () => {
        const user = userEvent.setup();
        render(<PastPlanDetailPage />);
        await screen.findByText('French Toast');

        expect(document.body.classList.contains('shopping-list-open')).toBe(false);

        await user.click(screen.getByRole('button', { name: /generate shopping list/i }));
        await screen.findByText('butter');
        expect(document.body.classList.contains('shopping-list-open')).toBe(true);

        await user.click(screen.getByRole('button', { name: /close shopping list/i }));
        expect(document.body.classList.contains('shopping-list-open')).toBe(false);
    });

    it('has a print button in the overlay that calls window.print', async () => {
        window.print = jest.fn();
        const user = userEvent.setup();
        render(<PastPlanDetailPage />);
        await screen.findByText('French Toast');

        await user.click(screen.getByRole('button', { name: /generate shopping list/i }));
        await screen.findByText('butter');

        await user.click(screen.getByRole('button', { name: /print shopping list/i }));
        expect(window.print).toHaveBeenCalledTimes(1);
    });

    it('wraps the page content in a print-hideable block that excludes the overlay', async () => {
        const user = userEvent.setup();
        const { container } = render(<PastPlanDetailPage />);
        await screen.findByText('French Toast');

        const printHide = container.querySelector('.shopping-list-print-hide');
        expect(printHide).not.toBeNull();
        // The page's own header and recipe list are hidden when printing...
        expect(printHide).toContainElement(
            screen.getByRole('heading', { name: /past plan/i })
        );
        expect(printHide).toContainElement(screen.getByText('French Toast'));

        // ...but the shopping list overlay is not inside the hidden block.
        await user.click(screen.getByRole('button', { name: /generate shopping list/i }));
        await screen.findByText('butter');
        const overlay = container.querySelector('.shopping-list-overlay');
        expect(overlay).not.toBeNull();
        expect(printHide).not.toContainElement(overlay as HTMLElement);
    });

    it('does not render any accept or reject controls', async () => {
        render(<PastPlanDetailPage />);
        await screen.findByText('French Toast');

        expect(screen.queryByRole('button', { name: /accept/i })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /reject/i })).not.toBeInTheDocument();
    });

    it('shows an error message when loading fails', async () => {
        (getAcceptedRecipes as jest.Mock).mockRejectedValue(new Error('boom'));
        render(<PastPlanDetailPage />);

        expect(await screen.findByRole('alert')).toHaveTextContent(/failed to load/i);
    });
});
