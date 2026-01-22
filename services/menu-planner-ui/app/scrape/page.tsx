'use client';

import RecipeScrapeForm from '@/app/components/RecipeScrapeForm';

export default function ScrapePage() {
    return (
        <div className="py-12 px-4 sm:px-6 lg:px-8">
            <div className="max-w-md mx-auto">
                <div className="text-center mb-8">
                    <h1 className="text-3xl font-bold text-gray-900">
                        Add Recipe from URL
                    </h1>
                    <p className="mt-2 text-sm text-gray-600">
                        Enter a recipe URL to scrape and add to your collection
                    </p>
                </div>

                <RecipeScrapeForm />
            </div>
        </div>
    );
}