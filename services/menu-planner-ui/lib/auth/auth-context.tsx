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
    const supabaseRef = useRef<SupabaseClient | null>(null);
    const router = useRouter();

    useEffect(() => {
        let unsubscribe: (() => void) | null = null;

        fetch('/api/config')
            .then(r => r.json())
            .then(({ supabaseUrl, supabaseAnonKey }) => {
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
            });

        return () => unsubscribe?.();
    }, []);

    const signIn = async (email: string, password: string) => {
        const { error } = await supabaseRef.current!.auth.signInWithPassword({ email, password });
        if (error) throw error;
        window.location.href = '/meal-plan';
    };

    const signUp = async (email: string, password: string, name: string) => {
        const { error } = await supabaseRef.current!.auth.signUp({
            email,
            password,
            options: { data: { name } },
        });
        if (error) throw error;
    };

    const signOut = async () => {
        await supabaseRef.current!.auth.signOut();
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
