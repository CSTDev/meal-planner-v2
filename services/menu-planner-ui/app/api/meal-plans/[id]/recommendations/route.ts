import { NextRequest, NextResponse } from 'next/server';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8080';

export async function GET(
    request: NextRequest,
    context: { params: Promise<{ id: string }> }  // params is now a Promise
) {
    try {
        // Await the params
        const params = await context.params;

        const searchParams = request.nextUrl.searchParams;
        const numRecipes = searchParams.get('num_recipes') || '5';

        const response = await fetch(
            `${API_GATEWAY_URL}/api/meal-plans/${params.id}/recommendations?num_recipes=${numRecipes}`,
            {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                },
            }
        );

        if (!response.ok) {
            throw new Error('Failed to get recommendations');
        }

        const data = await response.json();
        return NextResponse.json(data);
    } catch (error) {
        console.error('Error getting recommendations:', error);
        return NextResponse.json(
            { message: 'Failed to get recommendations' },
            { status: 500 }
        );
    }
}