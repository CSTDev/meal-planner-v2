import fs from 'fs';
import path from 'path';
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

    describe('split behind each total', () => {
        const render1 = (amounts: ShoppingListResponse['ingredients'][number]['amounts']) =>
            render(
                <ShoppingList
                    data={{ ingredients: [{ name: 'thing', amounts, breakdown: [] }] }}
                />
            );

        it('collapses identical parts with a multiplier', () => {
            render1([{ quantity: 500, unit: 'g', parts: [{ quantity: 250, unit: 'g', count: 2 }] }]);
            expect(screen.getByText('500 g (2 x 250 g)')).toBeInTheDocument();
        });

        it('joins differing parts with + in the given order', () => {
            render1([{
                quantity: 800, unit: 'g',
                parts: [{ quantity: 300, unit: 'g', count: 1 }, { quantity: 250, unit: 'g', count: 2 }],
            }]);
            expect(screen.getByText('800 g (300 g + 2 x 250 g)')).toBeInTheDocument();
        });

        it('keeps each part in its original unit', () => {
            render1([{
                quantity: 1500, unit: 'g',
                parts: [{ quantity: 1, unit: 'kg', count: 1 }, { quantity: 500, unit: 'g', count: 1 }],
            }]);
            expect(screen.getByText('1500 g (1 kg + 500 g)')).toBeInTheDocument();
        });

        it('shows no brackets for a single contributor', () => {
            render1([{ quantity: 250, unit: 'g', parts: [] }]);
            expect(screen.getByText('250 g')).toBeInTheDocument();
        });

        it('tolerates responses without parts', () => {
            render1([{ quantity: 250, unit: 'g' }]);
            expect(screen.getByText('250 g')).toBeInTheDocument();
        });

        it('renders unit-less counts', () => {
            render1([{
                quantity: 3, unit: null,
                parts: [{ quantity: 2, unit: null, count: 1 }, { quantity: 1, unit: null, count: 1 }],
            }]);
            expect(screen.getByText('3 (2 + 1)')).toBeInTheDocument();
        });

        it('is not hidden by the print stylesheet', () => {
            render1([{ quantity: 500, unit: 'g', parts: [{ quantity: 250, unit: 'g', count: 2 }] }]);
            const css = fs.readFileSync(path.join(process.cwd(), 'app/globals.css'), 'utf8');
            const start = css.indexOf('@media print');
            expect(start).toBeGreaterThanOrEqual(0);
            // Extract the @media print block by balancing braces
            const open = css.indexOf('{', start);
            let depth = 0;
            let end = open;
            for (; end < css.length; end++) {
                if (css[end] === '{') depth++;
                else if (css[end] === '}' && --depth === 0) break;
            }
            const printCss = css.slice(open + 1, end);
            const hiddenSelectors = Array.from(printCss.matchAll(/([^{}]+)\{([^{}]*)\}/g))
                .filter(([, , body]) => /display:\s*none/.test(body))
                .flatMap(([, selectors]) => selectors.split(','));
            expect(hiddenSelectors.length).toBeGreaterThan(0);

            const split = screen.getByText('500 g (2 x 250 g)');
            const elements: Element[] = [];
            for (let el: Element | null = split; el; el = el.parentElement) elements.push(el);
            elements.push(...Array.from(split.querySelectorAll('*')));
            const classes = elements.flatMap((el) => Array.from(el.classList));
            // No own, descendant or ancestor class may be hidden in print
            for (const cls of classes) {
                expect(hiddenSelectors.some((sel) => sel.includes(`.${cls}`))).toBe(false);
            }
        });
    });
});
