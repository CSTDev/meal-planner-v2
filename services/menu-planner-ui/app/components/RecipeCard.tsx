'use client';

import { Recipe } from '@/types/recipe';
import Image from 'next/image';

interface RecipeCardProps {
    recipe: Recipe;
    onAccept?: () => void;
    onReject?: () => void;
    showActions?: boolean;
}

export default function RecipeCard({
    recipe,
    onAccept,
    onReject,
    showActions = true
}: RecipeCardProps) {
    return (
        <div className="bg-white rounded-lg shadow-md overflow-hidden hover:shadow-lg transition-shadow">
            {/* Recipe Image */}
            <div className="relative h-48 bg-gray-200">
                {recipe.imageUrl ? (
                    <Image
                        src={recipe.imageUrl}
                        alt={recipe.title}
                        fill
                        className="object-cover"
                        sizes="(max-width: 768px) 100vw, (max-width: 1200px) 50vw, 33vw"
                    />
                ) : (
                    <div className="w-full h-full flex items-center justify-center">
                        <svg
                            className="w-16 h-16 text-gray-400"
                            fill="none"
                            stroke="currentColor"
                            viewBox="0 0 24 24"
                        >
                            <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                strokeWidth={2}
                                d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                            />
                        </svg>
                    </div>
                )}
            </div>

            {/* Recipe Details */}
            <div className="p-4">
                <h3 className="font-semibold text-gray-900 mb-2 line-clamp-2">
                    {recipe.title}
                </h3>

                <div className="flex items-center text-sm text-gray-600 mb-4">
                    <svg
                        className="w-4 h-4 mr-1"
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                    >
                        <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
                        />
                    </svg>
                    <span>
                        {recipe.totalTime
                            ? `${recipe.totalTime} min`
                            : recipe.prepTime && recipe.cookTime
                                ? `${recipe.prepTime + recipe.cookTime} min`
                                : 'Time not specified'}
                    </span>
                </div>

                {recipe.servings && (
                    <div className="text-sm text-gray-600 mb-4">
                        <span className="font-medium">Serves:</span> {recipe.servings}
                    </div>
                )}

                {showActions && (onAccept || onReject) && (
                    <div className="flex gap-2">
                        {onAccept && (
                            <button
                                onClick={onAccept}
                                className="flex-1 bg-green-600 hover:bg-green-700 text-white py-2 px-4 rounded-md transition font-medium"
                                title="Accept this recipe"
                            >
                                ✓ Accept
                            </button>
                        )}
                        {onReject && (
                            <button
                                onClick={onReject}
                                className="flex-1 bg-red-600 hover:bg-red-700 text-white py-2 px-4 rounded-md transition font-medium"
                                title="Get a different recipe"
                            >
                                ✗ Replace
                            </button>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}