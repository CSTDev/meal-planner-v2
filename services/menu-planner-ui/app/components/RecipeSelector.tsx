'use client';

import { useState, useEffect, useRef } from 'react';
import { searchRecipes } from '@/lib/api/recipes';
import { Recipe } from '@/types/recipe';

interface RecipeSelectorProps {
    mealPlanId: string;
    onSelect: (recipe: Recipe) => void;
    onCancel: () => void;
}

export default function RecipeSelector({
    mealPlanId,
    onSelect,
    onCancel
}: RecipeSelectorProps) {
    const [searchQuery, setSearchQuery] = useState('');
    const [suggestions, setSuggestions] = useState<Recipe[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [showDropdown, setShowDropdown] = useState(false);
    const inputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        const timer = setTimeout(async () => {
            if (searchQuery.length >= 2) {
                setIsLoading(true);
                try {
                    const results = await searchRecipes(searchQuery);
                    setSuggestions(results);
                    setShowDropdown(true);
                } catch (error) {
                    console.error('Failed to search recipes:', error);
                } finally {
                    setIsLoading(false);
                }
            } else {
                setSuggestions([]);
                setShowDropdown(false);
            }
        }, 300); // Debounce

        return () => clearTimeout(timer);
    }, [searchQuery]);

    const handleSelect = (recipe: Recipe) => {
        onSelect(recipe);
        setSearchQuery('');
        setSuggestions([]);
        setShowDropdown(false);
    };

    return (
        <div className="bg-white rounded-lg shadow-md p-4 space-y-4">
            <div className="relative">
                <input
                    ref={inputRef}
                    type="text"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder="Search for a recipe..."
                    className="w-full px-4 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none"
                    autoFocus
                />

                {isLoading && (
                    <div className="absolute right-3 top-3">
                        <svg
                            className="animate-spin h-5 w-5 text-gray-400"
                            xmlns="http://www.w3.org/2000/svg"
                            fill="none"
                            viewBox="0 0 24 24"
                        >
                            <circle
                                className="opacity-25"
                                cx="12"
                                cy="12"
                                r="10"
                                stroke="currentColor"
                                strokeWidth="4"
                            ></circle>
                            <path
                                className="opacity-75"
                                fill="currentColor"
                                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                            ></path>
                        </svg>
                    </div>
                )}

                {showDropdown && suggestions.length > 0 && (
                    <div className="absolute z-10 w-full mt-2 bg-white border border-gray-200 rounded-md shadow-lg max-h-96 overflow-y-auto">
                        {suggestions.map((recipe) => (
                            <button
                                key={recipe.id}
                                onClick={() => handleSelect(recipe)}
                                className="w-full text-left px-4 py-3 hover:bg-gray-50 border-b border-gray-100 last:border-b-0 transition"
                            >
                                <div className="flex items-center gap-3">
                                    {recipe.imageUrl && (
                                        <img
                                            src={recipe.imageUrl}
                                            alt={recipe.title}
                                            className="w-12 h-12 object-cover rounded"
                                        />
                                    )}
                                    <div className="flex-1 min-w-0">
                                        <p className="font-medium text-gray-900 truncate">
                                            {recipe.title}
                                        </p>
                                        {recipe.totalTime && (
                                            <p className="text-sm text-gray-500">
                                                {recipe.totalTime} min
                                            </p>
                                        )}
                                    </div>
                                </div>
                            </button>
                        ))}
                    </div>
                )}

                {showDropdown && searchQuery.length >= 2 && suggestions.length === 0 && !isLoading && (
                    <div className="absolute z-10 w-full mt-2 bg-white border border-gray-200 rounded-md shadow-lg p-4">
                        <p className="text-gray-500 text-sm">No recipes found</p>
                    </div>
                )}
            </div>

            <button
                onClick={onCancel}
                className="w-full py-2 px-4 border border-gray-300 rounded-md text-gray-700 hover:bg-gray-50 transition"
            >
                Cancel
            </button>
        </div>
    );
}