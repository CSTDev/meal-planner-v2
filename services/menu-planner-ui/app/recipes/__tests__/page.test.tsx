import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import RecipesPage from '@/app/recipes/page';

jest.mock('next/image', () => {
    const MockImage = ({ src, alt, fill: _fill, ...props }: { src: string; alt: string; fill?: boolean; [key: string]: unknown }) => (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={src} alt={alt} {...props} />
    );
    MockImage.displayName = 'MockImage';
    return MockImage;
});

const apiRecipes = [
    { id: '1', title: 'Chicken Curry', ingredients: [{ name: 'chicken' }, { name: 'rice' }], instructions: [] },
    { id: '2', title: 'Tomato Soup', ingredients: [{ name: 'tomatoes' }], instructions: [] },
    { id: '3', title: 'Pesto Pasta', ingredients: [{ name: 'basil' }, { name: 'pasta' }], instructions: [] },
];

function mockFetch(data: unknown[]) {
    global.fetch = jest.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(data) }) as jest.Mock;
}

async function renderLoaded(data: unknown[] = apiRecipes) {
    mockFetch(data);
    render(<RecipesPage />);
    await waitFor(() => expect(screen.queryByText('Loading recipes...')).not.toBeInTheDocument());
}

const search = (value: string) =>
    fireEvent.change(screen.getByRole('searchbox', { name: /search recipes/i }), { target: { value } });

describe('RecipesPage search', () => {
    it('shows all recipes and no count when not searching', async () => {
        await renderLoaded();
        expect(screen.getAllByRole('heading', { level: 3 })).toHaveLength(3);
        expect(screen.queryByText(/ of 3 recipes/)).not.toBeInTheDocument();
    });

    it('filters the grid as the user types, matching ingredients too', async () => {
        await renderLoaded();
        search('b');
        expect(screen.getByText('Pesto Pasta')).toBeInTheDocument();
        expect(screen.queryByText('Tomato Soup')).not.toBeInTheDocument();
        search('basil');
        expect(screen.getByText('Pesto Pasta')).toBeInTheDocument();
        expect(screen.queryByText('Chicken Curry')).not.toBeInTheDocument();
    });

    it('shows the result count while searching', async () => {
        await renderLoaded();
        search('soup');
        expect(screen.getByText('1 of 3 recipes')).toBeInTheDocument();
    });

    it('shows a no-match message with a working Clear search button', async () => {
        await renderLoaded();
        search('xyz');
        expect(screen.getByText('No recipes match "xyz"')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Clear search' }));
        expect(screen.getByRole('searchbox', { name: /search recipes/i })).toHaveValue('');
        expect(screen.getAllByRole('heading', { level: 3 })).toHaveLength(3);
        expect(screen.queryByText(/No recipes match/)).not.toBeInTheDocument();
    });

    it('clearing the query restores the full list', async () => {
        await renderLoaded();
        search('soup');
        search('');
        expect(screen.getAllByRole('heading', { level: 3 })).toHaveLength(3);
    });

    it('leaves the "No recipes yet" state unchanged for an empty collection', async () => {
        await renderLoaded([]);
        expect(screen.getByText('No recipes yet.')).toBeInTheDocument();
        expect(screen.queryByText(/No recipes match/)).not.toBeInTheDocument();
    });
});
