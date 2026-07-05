import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8080';

export async function POST(
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
        const body = await request.json();

        const response = await fetch(
            `${API_GATEWAY_URL}/api/meal-plans/${params.id}/feedback`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${session.access_token}`, // ✅ Add JWT token
                },
                body: JSON.stringify(body),
            }
        );

        if (!response.ok) {
            const errorText = await response.text();
            console.error('Backend error:', errorText);
            return NextResponse.json(
                { message: 'Failed to record feedback' },
                { status: response.status }
            );
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