import React from 'react';
import { render, screen } from '@testing-library/react';
import PastPlansPage from '@/app/past-plans/page';

jest.mock('@/lib/api/mealPlans', () => ({
    getPastMealPlans: jest.fn(),
}));

jest.mock('next/link', () => {
    const MockLink = ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
        <a href={href} {...props}>{children}</a>
    );
    MockLink.displayName = 'MockLink';
    return MockLink;
});

import { getPastMealPlans } from '@/lib/api/mealPlans';

const mockPlans = [
    {
        id: 'plan-1',
        createdAt: '2026-07-06T10:00:00.000Z',
        recipeSource: 'all',
        acceptedRecipeCount: 3,
    },
    {
        id: 'plan-2',
        createdAt: '2026-06-29T10:00:00.000Z',
        recipeSource: 'all',
        acceptedRecipeCount: 1,
    },
];

describe('PastPlansPage', () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('renders the page heading', async () => {
        (getPastMealPlans as jest.Mock).mockResolvedValue([]);
        render(<PastPlansPage />);
        expect(await screen.findByRole('heading', { name: /past plans/i })).toBeInTheDocument();
    });

    it('renders a row per plan linking to its detail page', async () => {
        (getPastMealPlans as jest.Mock).mockResolvedValue(mockPlans);
        render(<PastPlansPage />);

        const links = await screen.findAllByRole('link');
        expect(links).toHaveLength(2);
        expect(links[0]).toHaveAttribute('href', '/past-plans/plan-1');
        expect(links[1]).toHaveAttribute('href', '/past-plans/plan-2');
    });

    it('shows the plan date and accepted recipe count for each plan', async () => {
        (getPastMealPlans as jest.Mock).mockResolvedValue(mockPlans);
        render(<PastPlansPage />);

        expect(await screen.findByText('6 July 2026')).toBeInTheDocument();
        expect(screen.getByText('29 June 2026')).toBeInTheDocument();
        expect(screen.getByText('3 recipes')).toBeInTheDocument();
        expect(screen.getByText('1 recipe')).toBeInTheDocument();
    });

    it('shows the empty state when there are no qualifying plans', async () => {
        (getPastMealPlans as jest.Mock).mockResolvedValue([]);
        render(<PastPlansPage />);

        expect(
            await screen.findByText("You haven't created any meal plans with recipes yet.")
        ).toBeInTheDocument();
    });

    it('shows an error message when loading fails', async () => {
        (getPastMealPlans as jest.Mock).mockRejectedValue(new Error('boom'));
        render(<PastPlansPage />);

        expect(await screen.findByRole('alert')).toHaveTextContent(/failed to load past plans/i);
    });

    it('does not render any accept or reject controls', async () => {
        (getPastMealPlans as jest.Mock).mockResolvedValue(mockPlans);
        render(<PastPlansPage />);

        await screen.findAllByRole('link');
        expect(screen.queryByRole('button', { name: /accept/i })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /reject/i })).not.toBeInTheDocument();
    });
});
