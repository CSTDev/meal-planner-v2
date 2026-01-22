import { NextRequest, NextResponse } from 'next/server';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8080';

export async function POST(
    request: NextRequest,
    context: { params: Promise<{ id: string }> }  // params is now a Promise
) {
    try {
        // Await the params
        const params = await context.params;

        const body = await request.json();

        const response = await fetch(
            `${API_GATEWAY_URL}/api/meal-plans/${params.id}/feedback`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(body),
            }
        );

        if (!response.ok) {
            throw new Error('Failed to record feedback');
        }

        return NextResponse.json({ success: true });
    } catch (error) {
        console.error('Error recording feedback:', error);
        return NextResponse.json(
            { message: 'Failed to record feedback' },
            { status: 500 }
        );
    }
}