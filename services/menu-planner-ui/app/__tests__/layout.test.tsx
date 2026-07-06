import React from 'react';
import { render } from '@testing-library/react';
import RootLayout from '@/app/layout';

jest.mock('@/app/components/Sidebar', () => {
    const MockSidebar = () => <div data-testid="sidebar" />;
    MockSidebar.displayName = 'MockSidebar';
    return MockSidebar;
});

jest.mock('@/lib/auth/auth-context', () => ({
    AuthProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

describe('RootLayout – print classes', () => {
    it('outer shell div has app-shell class', () => {
        const { baseElement } = render(
            <RootLayout>
                <div data-testid="page-content" />
            </RootLayout>
        );
        expect(baseElement.querySelector('.app-shell')).toBeInTheDocument();
    });

    it('main element has app-main class', () => {
        const { baseElement } = render(
            <RootLayout>
                <div data-testid="page-content" />
            </RootLayout>
        );
        expect(baseElement.querySelector('.app-main')).toBeInTheDocument();
    });
});
