import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8080';

export async function POST(request: NextRequest) {
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

        const body = await request.json();

        // Forward to Java backend with JWT token
        const response = await fetch(`${API_GATEWAY_URL}/api/meal-plans`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${session.access_token}`, // ✅ Add JWT token
            },
            body: JSON.stringify({
                num_recipes: body.num_recipes,
                recipe_source: body.recipe_source,
            }),
        });

        if (!response.ok) {
            const errorText = await response.text();
            console.error('Backend error:', errorText);
            return NextResponse.json(
                { message: 'Failed to create meal plan' },
                { status: response.status }
            );
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