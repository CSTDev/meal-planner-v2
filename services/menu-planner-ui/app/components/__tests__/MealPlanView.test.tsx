import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import MealPlanView from '@/app/components/MealPlanView';
import { MealPlan } from '@/types/recipe';
import * as mealPlansApi from '@/lib/api/mealPlans';
import * as recipesApi from '@/lib/api/recipes';

jest.mock('@/lib/api/mealPlans');
jest.mock('@/lib/api/recipes');

// Stateful wrapper so onMealPlanUpdated changes are reflected in re-renders
function StatefulMealPlanView({ initialMealPlan }: { initialMealPlan: MealPlan }) {
    const [mealPlan, setMealPlan] = React.useState(initialMealPlan);
    return (
        <MealPlanView
            mealPlan={mealPlan}
            onMealPlanUpdated={setMealPlan}
            onReset={() => {}}
        />
    );
}

const recipe1 = {
    id: 'recipe-1',
    title: 'Pancakes',
    ingredients: [],
    instructionsList: [],
    canonicalUrl: 'https://example.com/pancakes',
    host: 'example.com',
};

const recipe2 = {
    id: 'recipe-2',
    title: 'Waffles',
    ingredients: [],
    instructionsList: [],
    canonicalUrl: 'https://example.com/waffles',
    host: 'example.com',
};

const recipe3 = {
    id: 'recipe-3',
    title: 'French Toast',
    ingredients: [],
    instructionsList: [],
    canonicalUrl: 'https://example.com/french-toast',
    host: 'example.com',
};

const mockMealPlan: MealPlan = {
    id: 'plan-1',
    userId: 'user-1',
    recipeSource: 'all',
    createdAt: '2026-01-01',
    status: 'ACTIVE',
    recipes: [recipe1, recipe2],
};

describe('MealPlanView shopping list integration', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        window.print = jest.fn();
    });

    it('fetches and displays the shopping list when the button is clicked', async () => {
        const user = userEvent.setup();
        (mealPlansApi.recordFeedback as jest.Mock).mockResolvedValue({ recipe: recipe1, status: 'ACCEPTED' });
        (mealPlansApi.getShoppingList as jest.Mock).mockResolvedValue({
            ingredients: [
                {
                    name: 'chicken breast',
                    amounts: [{ quantity: 680, unit: 'g' }],
                    breakdown: [
                        { recipeId: 'recipe-1', recipeTitle: 'Pancakes', quantity: 680, unit: 'g' },
                    ],
                },
            ],
        });

        render(
            <MealPlanView
                mealPlan={{ ...mockMealPlan, recipes: [recipe1] }}
                onMealPlanUpdated={jest.fn()}
                onReset={jest.fn()}
            />
        );

        // Accept the recipe so the shopping list button is enabled
        await user.click(screen.getAllByRole('button', { name: /accept/i })[0]);
        await waitFor(() => {
            expect(screen.getByText(/1 of 1 accepted/i)).toBeInTheDocument();
        });

        await user.click(screen.getByRole('button', { name: /view shopping list/i }));

        expect(mealPlansApi.getShoppingList).toHaveBeenCalledWith('plan-1');

        await waitFor(() => {
            expect(screen.getByText(/chicken breast/i)).toBeInTheDocument();
        });
    });

    it('shows a print button in the shopping list panel that calls window.print', async () => {
        const user = userEvent.setup();
        (mealPlansApi.recordFeedback as jest.Mock).mockResolvedValue({ recipe: recipe1, status: 'ACCEPTED' });
        (mealPlansApi.getShoppingList as jest.Mock).mockResolvedValue({ ingredients: [] });

        render(
            <MealPlanView
                mealPlan={{ ...mockMealPlan, recipes: [recipe1] }}
                onMealPlanUpdated={jest.fn()}
                onReset={jest.fn()}
            />
        );

        await user.click(screen.getAllByRole('button', { name: /accept/i })[0]);
        await waitFor(() => {
            expect(screen.getByText(/1 of 1 accepted/i)).toBeInTheDocument();
        });

        await user.click(screen.getByRole('button', { name: /view shopping list/i }));

        expect(screen.getByRole('button', { name: /print shopping list/i })).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: /print shopping list/i }));
        expect(window.print).toHaveBeenCalledTimes(1);
    });
});

describe('MealPlanView acceptance state', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        (mealPlansApi.recordFeedback as jest.Mock).mockResolvedValue({ recipe: recipe1, status: 'ACCEPTED' });
    });

    it('clicking Accept renders the accepted state for that card and updates the count', async () => {
        const user = userEvent.setup();

        render(
            <MealPlanView
                mealPlan={mockMealPlan}
                onMealPlanUpdated={jest.fn()}
                onReset={jest.fn()}
            />
        );

        // Initially 0 of 2 accepted
        expect(screen.getByText(/0 of 2 accepted/i)).toBeInTheDocument();

        // Accept the first recipe (Pancakes)
        const acceptButtons = screen.getAllByRole('button', { name: /✓ accept/i });
        await user.click(acceptButtons[0]);

        // Card should now show accepted state
        await waitFor(() => {
            expect(screen.getByText(/accepted ✓/i)).toBeInTheDocument();
        });

        // Count updates
        expect(screen.getByText(/1 of 2 accepted/i)).toBeInTheDocument();

        expect(mealPlansApi.recordFeedback).toHaveBeenCalledWith('plan-1', 'recipe-1', 'accepted');
        expect(mealPlansApi.recordFeedback).toHaveBeenCalledTimes(1);
    });

    it('accept is idempotent in the UI: accepted button no longer invokes the API', async () => {
        const user = userEvent.setup();

        render(
            <MealPlanView
                mealPlan={{ ...mockMealPlan, recipes: [recipe1] }}
                onMealPlanUpdated={jest.fn()}
                onReset={jest.fn()}
            />
        );

        await user.click(screen.getByRole('button', { name: /✓ accept/i }));

        await waitFor(() => {
            expect(screen.getByText(/accepted ✓/i)).toBeInTheDocument();
        });

        // The original "✓ Accept" button should no longer exist
        expect(screen.queryByRole('button', { name: /✓ accept/i })).toBeNull();

        // recordFeedback called exactly once
        expect(mealPlansApi.recordFeedback).toHaveBeenCalledTimes(1);
    });

    it('shows a hint and disables shopping list when zero recipes are accepted', async () => {
        render(
            <MealPlanView
                mealPlan={mockMealPlan}
                onMealPlanUpdated={jest.fn()}
                onReset={jest.fn()}
            />
        );

        // Shopping list button should be disabled or show hint text
        const shoppingListBtn = screen.getByRole('button', { name: /view shopping list/i });
        expect(shoppingListBtn).toBeDisabled();
    });

    it('shopping list button reflects accepted count and is enabled after accepting', async () => {
        const user = userEvent.setup();
        (mealPlansApi.getShoppingList as jest.Mock).mockResolvedValue({ ingredients: [] });

        render(
            <MealPlanView
                mealPlan={mockMealPlan}
                onMealPlanUpdated={jest.fn()}
                onReset={jest.fn()}
            />
        );

        // Initially disabled
        expect(screen.getByRole('button', { name: /view shopping list/i })).toBeDisabled();

        // Accept recipe 1
        const acceptButtons = screen.getAllByRole('button', { name: /✓ accept/i });
        await user.click(acceptButtons[0]);

        await waitFor(() => {
            // Button becomes enabled with count
            expect(screen.getByRole('button', { name: /view shopping list \(1\)/i })).not.toBeDisabled();
        });
    });

    it('a failed recordFeedback reverts the card to proposed state and shows an error', async () => {
        const user = userEvent.setup();
        (mealPlansApi.recordFeedback as jest.Mock).mockRejectedValue(new Error('network error'));

        render(
            <MealPlanView
                mealPlan={{ ...mockMealPlan, recipes: [recipe1] }}
                onMealPlanUpdated={jest.fn()}
                onReset={jest.fn()}
            />
        );

        await user.click(screen.getByRole('button', { name: /✓ accept/i }));

        // Card reverts — accept button returns
        await waitFor(() => {
            expect(screen.getByRole('button', { name: /✓ accept/i })).toBeInTheDocument();
        });

        // Error is surfaced
        expect(screen.getByText(/failed to accept/i)).toBeInTheDocument();

        // Count stays at 0
        expect(screen.getByText(/0 of 1 accepted/i)).toBeInTheDocument();
    });

    it('Choose Different renders the replacement in the accepted state', async () => {
        const user = userEvent.setup();
        (recipesApi.searchRecipes as jest.Mock).mockResolvedValue([recipe3]);
        (mealPlansApi.recordFeedback as jest.Mock).mockResolvedValue({ recipe: recipe3, status: 'ACCEPTED' });

        // StatefulWrapper so onMealPlanUpdated actually re-renders with the new recipe
        render(<StatefulMealPlanView initialMealPlan={{ ...mockMealPlan, recipes: [recipe1] }} />);

        // Open RecipeSelector for slot 0
        await user.click(screen.getByRole('button', { name: /choose different/i }));

        // Type in the search box
        const searchInput = screen.getByPlaceholderText(/search for a recipe/i);
        await user.type(searchInput, 'French');

        // Wait for search results
        await waitFor(() => {
            expect(screen.getByText('French Toast')).toBeInTheDocument();
        });

        // Select the replacement
        await user.click(screen.getByText('French Toast'));

        // Replacement card should show accepted state
        await waitFor(() => {
            expect(screen.getByText(/accepted ✓/i)).toBeInTheDocument();
        });

        // A single atomic call: reject the old recipe, replace with the new one
        expect(mealPlansApi.recordFeedback).toHaveBeenCalledWith('plan-1', 'recipe-1', 'rejected', 'recipe-3');
        expect(mealPlansApi.recordFeedback).toHaveBeenCalledTimes(1);
    });

    it('random Replace (plain reject) renders the replacement from the feedback response as proposed (not accepted)', async () => {
        const user = userEvent.setup();
        (mealPlansApi.recordFeedback as jest.Mock).mockResolvedValue({ recipe: recipe3, status: 'OFFERED' });

        render(
            <MealPlanView
                mealPlan={{ ...mockMealPlan, recipes: [recipe1] }}
                onMealPlanUpdated={jest.fn()}
                onReset={jest.fn()}
            />
        );

        // Reject recipe 1 (random replace)
        await user.click(screen.getByRole('button', { name: /✗ replace/i }));

        // Replacement appears as proposed (no "Accepted ✓")
        await waitFor(() => {
            expect(screen.queryByText(/accepted ✓/i)).toBeNull();
        });

        // The new recipe shows a normal Accept button
        expect(screen.getByRole('button', { name: /✓ accept/i })).toBeInTheDocument();

        // Count stays 0 of 1
        expect(screen.getByText(/0 of 1 accepted/i)).toBeInTheDocument();

        expect(mealPlansApi.recordFeedback).toHaveBeenCalledWith('plan-1', 'recipe-1', 'rejected');
    });

    it('a plain reject that exhausts the pool removes the slot and shows the aggregate banner', async () => {
        const user = userEvent.setup();
        (mealPlansApi.recordFeedback as jest.Mock).mockResolvedValue({ recipe: null, status: 'REMOVED' });

        render(<StatefulMealPlanView initialMealPlan={{ ...mockMealPlan, recipes: [recipe1, recipe2] }} />);

        await user.click(screen.getAllByRole('button', { name: /✗ replace/i })[0]);

        await waitFor(() => {
            expect(screen.queryByText('Pancakes')).not.toBeInTheDocument();
        });

        // Only the remaining slot's recipe is shown
        expect(screen.getByText('Waffles')).toBeInTheDocument();

        // Aggregate banner: 1 of 2 recipes could be filled
        expect(screen.getByText(/1 of 2 recipes could be filled/i)).toBeInTheDocument();
        expect(screen.getByText(/0 of 1 accepted/i)).toBeInTheDocument();
    });

    it('Choose Different shows an error when the replace call fails', async () => {
        const user = userEvent.setup();
        (recipesApi.searchRecipes as jest.Mock).mockResolvedValue([recipe3]);
        (mealPlansApi.recordFeedback as jest.Mock).mockRejectedValue(new Error('network error'));

        render(<StatefulMealPlanView initialMealPlan={{ ...mockMealPlan, recipes: [recipe1] }} />);

        await user.click(screen.getByRole('button', { name: /choose different/i }));

        const searchInput = screen.getByPlaceholderText(/search for a recipe/i);
        await user.type(searchInput, 'French');

        await waitFor(() => {
            expect(screen.getByText('French Toast')).toBeInTheDocument();
        });

        await user.click(screen.getByText('French Toast'));

        await waitFor(() => {
            expect(screen.getByRole('alert')).toBeInTheDocument();
        });

        expect(screen.getByRole('alert').textContent).toMatch(/failed to replace/i);
    });

    it('error message is dismissed when the user rejects a recipe after a failed accept', async () => {
        (mealPlansApi.recordFeedback as jest.Mock)
            .mockRejectedValueOnce(new Error('network error'))  // first call: accept fails
            .mockResolvedValue({ recipe: recipe3, status: 'OFFERED' });  // subsequent calls: succeed

        const user = userEvent.setup();

        render(
            <MealPlanView
                mealPlan={mockMealPlan}
                onMealPlanUpdated={jest.fn()}
                onReset={jest.fn()}
            />
        );

        // Fail the accept on recipe 1
        await user.click(screen.getAllByRole('button', { name: /✓ accept/i })[0]);
        await waitFor(() => {
            expect(screen.getByRole('alert')).toBeInTheDocument();
        });

        // Reject recipe 2 — the error should clear
        await user.click(screen.getAllByRole('button', { name: /✗ replace/i })[1]);

        await waitFor(() => {
            expect(screen.queryByRole('alert')).toBeNull();
        });
    });

    it('error message is dismissed when the user chooses a different recipe after a failed accept', async () => {
        (mealPlansApi.recordFeedback as jest.Mock)
            .mockRejectedValueOnce(new Error('network error'))  // first call: accept fails
            .mockResolvedValue({ recipe: recipe3, status: 'ACCEPTED' });  // subsequent calls: succeed
        (recipesApi.searchRecipes as jest.Mock).mockResolvedValue([recipe3]);

        const user = userEvent.setup();

        render(<StatefulMealPlanView initialMealPlan={{ ...mockMealPlan, recipes: [recipe1] }} />);

        // Fail the accept on recipe 1 — alert should appear
        await user.click(screen.getByRole('button', { name: /✓ accept/i }));
        await waitFor(() => {
            expect(screen.getByRole('alert')).toBeInTheDocument();
        });

        // Drive the "Choose Different" flow (triggers handleReplaceWithSpecific)
        await user.click(screen.getByRole('button', { name: /choose different/i }));

        const searchInput = screen.getByPlaceholderText(/search for a recipe/i);
        await user.type(searchInput, 'French');

        await waitFor(() => {
            expect(screen.getByText('French Toast')).toBeInTheDocument();
        });

        await user.click(screen.getByText('French Toast'));

        // Alert should be gone after replacement completes
        await waitFor(() => {
            expect(screen.queryByRole('alert')).toBeNull();
        });
    });

    it('accepting a recipe then replacing a different slot with it only fires one ACCEPTED call', async () => {
        const user = userEvent.setup();
        // Search returns recipe-1 (which will already be accepted)
        (recipesApi.searchRecipes as jest.Mock).mockResolvedValue([recipe1]);
        (mealPlansApi.recordFeedback as jest.Mock).mockResolvedValue({ recipe: recipe1, status: 'ACCEPTED' });

        const onMealPlanUpdated = jest.fn();

        render(
            <MealPlanView
                mealPlan={mockMealPlan}
                onMealPlanUpdated={onMealPlanUpdated}
                onReset={jest.fn()}
            />
        );

        // Accept recipe-1 directly
        const acceptButtons = screen.getAllByRole('button', { name: /✓ accept/i });
        await user.click(acceptButtons[0]);
        await waitFor(() => {
            expect(screen.getByText(/1 of 2 accepted/i)).toBeInTheDocument();
        });

        // Now "Choose Different" for slot 2 (recipe-2), but select recipe-1 again
        const chooseDifferentButtons = screen.getAllByRole('button', { name: /choose different/i });
        await user.click(chooseDifferentButtons[1]);

        const searchInput = screen.getByPlaceholderText(/search for a recipe/i);
        await user.type(searchInput, 'Pancakes');

        // "Pancakes" also appears as slot 0's already-accepted card title, so
        // scope to the dropdown suggestion button specifically.
        const dropdownOption = await screen.findByRole('button', { name: 'Pancakes' });
        await user.click(dropdownOption);

        // The direct accept fires action 'accepted'; the replace fires action
        // 'rejected' with a replacement_recipe_id — 'accepted' is invoked
        // exactly once overall.
        await waitFor(() => {
            const acceptedCalls = (mealPlansApi.recordFeedback as jest.Mock).mock.calls.filter(
                ([, , action]) => action === 'accepted'
            );
            expect(acceptedCalls).toHaveLength(1);
        });
        const replaceCalls = (mealPlansApi.recordFeedback as jest.Mock).mock.calls.filter(
            ([, , action]) => action === 'rejected'
        );
        expect(replaceCalls).toHaveLength(1);
        expect(replaceCalls[0]).toEqual(['plan-1', 'recipe-2', 'rejected', 'recipe-1']);
    });
});
