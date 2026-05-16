import { NextResponse } from 'next/server';

export async function GET() {
    return NextResponse.json({
        supabaseUrl: process.env.SUPABASE_PUBLIC_URL ?? process.env.SUPABASE_URL,
        supabaseAnonKey: process.env.SUPABASE_ANON_KEY,
    });
}
