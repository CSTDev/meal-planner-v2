import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import RecipeSelector from '@/app/components/RecipeSelector';
import * as recipesApi from '@/lib/api/recipes';

jest.mock('@/lib/api/recipes');

const mockRecipe1 = {
    id: 'recipe-1',
    title: 'Chicken Tikka Masala',
    ingredients: [],
    instructionsList: [],
    canonicalUrl: 'https://example.com/tikka',
    host: 'example.com',
};

const mockRecipe2 = {
    id: 'recipe-2',
    title: 'Waffles',
    ingredients: [],
    instructionsList: [],
    canonicalUrl: 'https://example.com/waffles',
    host: 'example.com',
};

describe('RecipeSelector', () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('typing >= 2 characters triggers search and renders one row per returned recipe', async () => {
        const user = userEvent.setup();
        (recipesApi.searchRecipes as jest.Mock).mockResolvedValue([mockRecipe1, mockRecipe2]);

        render(
            <RecipeSelector
                mealPlanId="plan-1"
                onSelect={jest.fn()}
                onCancel={jest.fn()}
            />
        );

        const input = screen.getByPlaceholderText(/search for a recipe/i);
        await user.type(input, 'ti');

        await waitFor(() => {
            expect(screen.getByText('Chicken Tikka Masala')).toBeInTheDocument();
            expect(screen.getByText('Waffles')).toBeInTheDocument();
        });

        expect(recipesApi.searchRecipes).toHaveBeenCalledWith('plan-1', 'ti');
    });

    it('fewer than 2 characters shows no dropdown and fires no request', async () => {
        const user = userEvent.setup();
        (recipesApi.searchRecipes as jest.Mock).mockResolvedValue([mockRecipe1]);

        render(
            <RecipeSelector
                mealPlanId="plan-1"
                onSelect={jest.fn()}
                onCancel={jest.fn()}
            />
        );

        const input = screen.getByPlaceholderText(/search for a recipe/i);
        await user.type(input, 't');

        // No dropdown should appear; the debounce fires but search length < 2 so API is not called
        expect(screen.queryByText('Chicken Tikka Masala')).not.toBeInTheDocument();
        expect(recipesApi.searchRecipes).not.toHaveBeenCalled();
    });

    it('empty list from API renders the No recipes found state', async () => {
        const user = userEvent.setup();
        (recipesApi.searchRecipes as jest.Mock).mockResolvedValue([]);

        render(
            <RecipeSelector
                mealPlanId="plan-1"
                onSelect={jest.fn()}
                onCancel={jest.fn()}
            />
        );

        const input = screen.getByPlaceholderText(/search for a recipe/i);
        await user.type(input, 'xyz');

        await waitFor(() => {
            expect(screen.getByText(/no recipes found/i)).toBeInTheDocument();
        });
    });

    it('selecting a row calls onSelect with the recipe and clears the input', async () => {
        const user = userEvent.setup();
        const onSelect = jest.fn();
        (recipesApi.searchRecipes as jest.Mock).mockResolvedValue([mockRecipe1]);

        render(
            <RecipeSelector
                mealPlanId="plan-1"
                onSelect={onSelect}
                onCancel={jest.fn()}
            />
        );

        const input = screen.getByPlaceholderText(/search for a recipe/i);
        await user.type(input, 'tikka');

        await waitFor(() => {
            expect(screen.getByText('Chicken Tikka Masala')).toBeInTheDocument();
        });

        await user.click(screen.getByText('Chicken Tikka Masala'));

        expect(onSelect).toHaveBeenCalledWith(mockRecipe1);
        expect(input).toHaveValue('');
    });
});
