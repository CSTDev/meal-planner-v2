import { NextRequest, NextResponse } from 'next/server';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8080';

export async function POST(request: NextRequest) {
    try {
        const body = await request.json();
        const { num_recipes, recipe_source } = body;

        // TODO: Get actual user ID from authentication
        const userId = 'test-user-123';

        const response = await fetch(`${API_GATEWAY_URL}/api/meal-plans`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                user_id: userId,
                num_recipes,
                recipe_source,
            }),
        });

        if (!response.ok) {
            throw new Error('Failed to create meal plan');
        }

        const data = await response.json();
        return NextResponse.json(data);
    } catch (error) {
        console.error('Error creating meal plan:', error);
        return NextResponse.json(
            { message: 'Failed to create meal plan' },
            { status: 500 }
        );
    }
}