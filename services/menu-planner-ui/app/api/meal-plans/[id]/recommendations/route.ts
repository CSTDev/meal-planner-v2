import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8080';

export async function GET(
    request: NextRequest,
    context: { params: Promise<{ id: string }> }
) {
    try {
        // Get Supabase session
        const supabase = await createClient();
        const { data: { session }, error: sessionError } = await supabase.auth.getSession();

        if (sessionError || !session) {
            return NextResponse.json(
                { message: 'Unauthorized - No valid session' },
                { status: 401 }
            );
        }

        const params = await context.params;
        const searchParams = request.nextUrl.searchParams;
        const numRecipes = searchParams.get('num_recipes') || '5';

        const response = await fetch(
            `${API_GATEWAY_URL}/api/meal-plans/${params.id}/recommendations?num_recipes=${numRecipes}`,
            {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${session.access_token}`, // ✅ Add JWT token
                },
            }
        );

        if (!response.ok) {
            const errorText = await response.text();
            console.error('Backend error:', errorText);
            return NextResponse.json(
                { message: 'Failed to get recommendations' },
                { status: response.status }
            );
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