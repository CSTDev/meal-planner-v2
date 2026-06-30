'use client';

import { createContext, useContext, useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { createClient } from '@/lib/supabase/client';
import type { SupabaseClient, User, Session } from '@supabase/supabase-js';

interface AuthContextType {
    user: User | null;
    session: Session | null;
    signIn: (email: string, password: string) => Promise<void>;
    signUp: (email: string, password: string, name: string) => Promise<void>;
    signOut: () => Promise<void>;
    isLoading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
    const [user, setUser] = useState<User | null>(null);
    const [session, setSession] = useState<Session | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [initError, setInitError] = useState<Error | null>(null);
    const supabaseRef = useRef<SupabaseClient | null>(null);
    const router = useRouter();

    useEffect(() => {
        let unsubscribe: (() => void) | null = null;

        fetch('/api/config')
            .then(r => {
                if (!r.ok) {
                    throw new Error(`Failed to load auth config: ${r.status} ${r.statusText}`);
                }
                return r.json();
            })
            .then(({ supabaseUrl, supabaseAnonKey }) => {
                if (!supabaseUrl || !supabaseAnonKey) {
                    throw new Error('Auth config is missing supabaseUrl or supabaseAnonKey');
                }

                const supabase = createClient(supabaseUrl, supabaseAnonKey);
                supabaseRef.current = supabase;

                supabase.auth.getSession().then(({ data: { session } }) => {
                    setSession(session);
                    setUser(session?.user ?? null);
                    setIsLoading(false);
                });

                const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
                    setSession(session);
                    setUser(session?.user ?? null);
                    setIsLoading(false);
                });
                unsubscribe = () => subscription.unsubscribe();
            })
            .catch((err) => {
                console.error('Failed to initialize authentication:', err);
                setInitError(err instanceof Error ? err : new Error(String(err)));
                setIsLoading(false);
            });

        return () => unsubscribe?.();
    }, []);

    const requireClient = () => {
        if (!supabaseRef.current) {
            throw initError ?? new Error('Authentication is not ready yet. Please refresh and try again.');
        }
        return supabaseRef.current;
    };

    const signIn = async (email: string, password: string) => {
        const { error } = await requireClient().auth.signInWithPassword({ email, password });
        if (error) throw error;
        window.location.href = '/meal-plan';
    };

    const signUp = async (email: string, password: string, name: string) => {
        const { error } = await requireClient().auth.signUp({
            email,
            password,
            options: { data: { name } },
        });
        if (error) throw error;
    };

    const signOut = async () => {
        await requireClient().auth.signOut();
        router.push('/login');
        router.refresh();
    };

    return (
        <AuthContext.Provider value={{ user, session, signIn, signUp, signOut, isLoading }}>
            {children}
        </AuthContext.Provider>
    );
}

export const useAuth = () => {
    const context = useContext(AuthContext);
    if (context === undefined) {
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
};
