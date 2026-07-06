import './globals.css';
import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import Sidebar from '@/app/components/Sidebar';
import { AuthProvider } from '@/lib/auth/auth-context';

const inter = Inter({ subsets: ['latin'] });

export const metadata: Metadata = {
  title: 'Recipe Meal Planner',
  description: 'Plan your meals with personalized recipe recommendations',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className={inter.className}>
        <AuthProvider>
          <div className="flex h-screen bg-gray-50 app-shell">
            <Sidebar />
            <main className="flex-1 overflow-y-auto app-main">
              {children}
            </main>
          </div>
        </AuthProvider>
      </body>
    </html>
  );
}