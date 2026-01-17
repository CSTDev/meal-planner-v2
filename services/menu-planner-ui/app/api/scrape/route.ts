import { NextRequest, NextResponse } from 'next/server';

// This would typically come from environment variables
const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8080';

export async function POST(request: NextRequest) {
    try {
        const body = await request.json();
        const { url } = body;

        // Validate request
        if (!url) {
            return NextResponse.json(
                { message: 'URL is required' },
                { status: 400 }
            );
        }

        // Validate URL format
        try {
            new URL(url);
        } catch (error) {
            return NextResponse.json(
                { message: 'Invalid URL format' },
                { status: 400 }
            );
        }

        // TODO: Get actual user ID from authentication
        const userId = 'test-user-123'; // Placeholder

        // Forward to API Gateway
        const response = await fetch(`${API_GATEWAY_URL}/api/scrape`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                // Add authentication headers here if needed
                // 'Authorization': `Bearer ${token}`,
            },
            body: JSON.stringify({
                url,
                //user_id: userId,
            }),
        });

        if (!response.ok) {
            const error = await response.json().catch(() => ({
                message: 'Failed to submit to API gateway',
            }));

            return NextResponse.json(
                { message: error.message || 'Failed to submit recipe for scraping' },
                { status: response.status }
            );
        }

        const data = {};

        return NextResponse.json({
            message: 'Recipe scraping initiated',
            url,
            ...data,
        });
    } catch (error) {
        console.error('Error in scrape API route:', error);

        return NextResponse.json(
            { message: 'Internal server error' },
            { status: 500 }
        );
    }
}