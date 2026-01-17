const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:3000';

export interface ScrapeRecipeRequest {
    url: string;
}

export interface ScrapeRecipeResponse {
    message: string;
    url: string;
}

/**
 * Submit a recipe URL for scraping
 */
export async function scrapeRecipe(url: string): Promise<ScrapeRecipeResponse> {
    const response = await fetch(`${API_BASE_URL}/api/scrape`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({ url }),
    });

    if (!response.ok) {
        const error = await response.json().catch(() => ({
            message: 'Failed to submit recipe for scraping',
        }));
        throw new Error(error.message || 'Failed to submit recipe for scraping');
    }

    return response.json();
}