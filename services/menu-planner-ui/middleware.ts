import { type NextRequest, NextResponse } from 'next/server';
import { createServerClient } from '@supabase/ssr';

export async function middleware(request: NextRequest) {
    const supabaseResponse = NextResponse.next({
        request,
    });

    const supabase = createServerClient(
        process.env.NEXT_PUBLIC_SUPABASE_URL!,
        process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
        {
            cookies: {
                getAll() {
                    return request.cookies.getAll();
                },
                setAll(cookiesToSet) {
                    cookiesToSet.forEach(({ name, value, options }) => {
                        request.cookies.set(name, value);
                        supabaseResponse.cookies.set(name, value, options);
                    });
                },
            },
        }
    );

    // Refresh session if expired
    await supabase.auth.getSession();

    const { pathname } = request.nextUrl;

    // Public routes that don't need authentication
    const publicRoutes = ['/login', '/signup', '/'];
    const isPublicRoute = publicRoutes.some(route => pathname.startsWith(route));

    if (isPublicRoute) {
        return supabaseResponse;
    }

    // For protected routes, check if we have a session
    const { data: { session } } = await supabase.auth.getSession();

    if (!session) {
        const redirectUrl = new URL('/login', request.url);
        return NextResponse.redirect(redirectUrl);
    }

    return supabaseResponse;
}

export const config = {
    matcher: [
        '/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)',
    ],
};