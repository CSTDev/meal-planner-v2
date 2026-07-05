import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';

const API_GATEWAY_URL = process.env.API_GATEWAY_URL || 'http://localhost:8080';

export async function GET(_request: NextRequest) {
    try {
        // const searchParams = request.nextUrl.searchParams;
        // const query = searchParams.get('q');

        // if (!query) {
        //     return NextResponse.json(
        //         { message: 'Query parameter required' },
        //         { status: 400 }
        //     );
        // }

        // Get Supabase session
        const supabase = await createClient();
        const { data: { session }, error: sessionError } = await supabase.auth.getSession();

        if (sessionError || !session) {
            return NextResponse.json(
                { message: 'Unauthorized - No valid session' },
                { status: 401 }
            );
        }

        const response = await fetch(
            `${API_GATEWAY_URL}/api/recipes`,
            {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${session.access_token}`,
                },
            }
        );

        if (!response.ok) {
            const errorText = await response.text();
            console.error('Backend error:', errorText);
            return NextResponse.json(
                { message: 'Failed to search recipes' },
                { status: response.status }
            );
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