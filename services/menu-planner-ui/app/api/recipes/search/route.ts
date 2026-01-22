import { NextRequest, NextResponse } from 'next/server';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8080';

export async function GET(request: NextRequest) {
    try {
        const searchParams = request.nextUrl.searchParams;
        const query = searchParams.get('q');

        if (!query) {
            return NextResponse.json(
                { message: 'Query parameter required' },
                { status: 400 }
            );
        }

        // TODO: Get actual user ID from authentication
        const userId = 'test-user-123';

        const response = await fetch(
            `${API_GATEWAY_URL}/api/users/${userId}/recipes?search=${encodeURIComponent(query)}`,
            {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                },
            }
        );

        if (!response.ok) {
            throw new Error('Failed to search recipes');
        }

        const data = await response.json();
        return NextResponse.json(data);
    } catch (error) {
        console.error('Error searching recipes:', error);
        return NextResponse.json(
            { message: 'Failed to search recipes' },
            { status: 500 }
        );
    }
}