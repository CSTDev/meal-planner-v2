'use client';

import { useState } from 'react';
import { ShoppingListIngredient, ShoppingListResponse } from '@/types/recipe';

interface ShoppingListProps {
    data: ShoppingListResponse;
}

function formatAmount(quantity: number | null, unit: string | null): string {
    if (quantity === null || quantity === undefined) {
        return '';
    }
    const roundedQuantity = Number.isInteger(quantity)
        ? quantity
        : Math.round(quantity * 100) / 100;
    return unit ? `${roundedQuantity} ${unit}` : `${roundedQuantity}`;
}

function IngredientRow({ ingredient }: { ingredient: ShoppingListIngredient }) {
    const [expanded, setExpanded] = useState(false);

    const amountsText = ingredient.amounts
        .map((amount) => formatAmount(amount.quantity, amount.unit))
        .filter(Boolean)
        .join(' + ');

    return (
        <li className="border-b border-gray-200 py-3">
            <button
                type="button"
                onClick={() => setExpanded((prev) => !prev)}
                aria-expanded={expanded}
                className="w-full flex items-center justify-between text-left"
            >
                <span className="font-medium text-gray-900 capitalize">
                    {ingredient.name}
                </span>
                <span className="flex items-center gap-2 text-sm text-gray-600">
                    {amountsText && <span>{amountsText}</span>}
                    <span className="ingredient-chevron text-gray-400">{expanded ? '▲' : '▼'}</span>
                </span>
            </button>

            {expanded && (
                <ul className="ingredient-breakdown mt-2 pl-4 space-y-1 text-sm text-gray-600">
                    {ingredient.breakdown.map((entry, index) => (
                        <li key={`${entry.recipeId}-${index}`} className="flex justify-between">
                            <span>{entry.recipeTitle}</span>
                            <span>
                                {entry.quantity !== null
                                    ? formatAmount(entry.quantity, entry.unit)
                                    : 'to taste'}
                            </span>
                        </li>
                    ))}
                </ul>
            )}
        </li>
    );
}

export default function ShoppingList({ data }: ShoppingListProps) {
    if (!data.ingredients || data.ingredients.length === 0) {
        return (
            <div className="py-8 text-center text-gray-500">
                No ingredients — accept some recipes into this meal plan first.
            </div>
        );
    }

    return (
        <ul>
            {data.ingredients.map((ingredient) => (
                <IngredientRow key={ingredient.name} ingredient={ingredient} />
            ))}
        </ul>
    );
}
