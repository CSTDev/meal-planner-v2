import React from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Sidebar from '@/app/components/Sidebar';

jest.mock('next/navigation', () => ({
    usePathname: jest.fn(() => '/meal-plan'),
}));

jest.mock('@/lib/auth/auth-context', () => ({
    useAuth: jest.fn(),
}));

jest.mock('next/link', () => {
    const MockLink = ({ href, children, onClick, ...props }: { href: string; children: React.ReactNode; onClick?: () => void; [key: string]: unknown }) => (
        <a href={href} onClick={onClick} {...props}>{children}</a>
    );
    MockLink.displayName = 'MockLink';
    return MockLink;
});

import { useAuth } from '@/lib/auth/auth-context';

const mockUser = {
    email: 'test@example.com',
    user_metadata: { name: 'Test User' },
};

function renderSidebar(user = mockUser) {
    (useAuth as jest.Mock).mockReturnValue({ user, signOut: jest.fn() });
    return render(<Sidebar />);
}

describe('Sidebar – unauthenticated', () => {
    it('renders nothing when there is no user', () => {
        (useAuth as jest.Mock).mockReturnValue({ user: null, signOut: jest.fn() });
        const { container } = render(<Sidebar />);
        expect(container).toBeEmptyDOMElement();
    });

    it('does not render the mobile rail spacer when unauthenticated', () => {
        (useAuth as jest.Mock).mockReturnValue({ user: null, signOut: jest.fn() });
        render(<Sidebar />);
        expect(screen.queryByTestId('sidebar-mobile-spacer')).not.toBeInTheDocument();
    });
});

describe('Sidebar – mobile rail spacer', () => {
    it('renders a spacer element when authenticated so main content clears the fixed rail', () => {
        renderSidebar();
        expect(screen.getByTestId('sidebar-mobile-spacer')).toBeInTheDocument();
    });
});

describe('Sidebar – print class', () => {
    it('applies app-sidebar class to exactly the mobile section, spacer, and desktop sidebar', () => {
        const { container } = renderSidebar();
        const appSidebarElements = container.querySelectorAll('.app-sidebar');
        expect(appSidebarElements.length).toBe(3);
    });

    it('mobile section has app-sidebar class', () => {
        renderSidebar();
        expect(screen.getByTestId('sidebar-mobile-section')).toHaveClass('app-sidebar');
    });

    it('desktop sidebar has app-sidebar class', () => {
        renderSidebar();
        expect(screen.getByTestId('sidebar-desktop')).toHaveClass('app-sidebar');
    });

    it('mobile spacer has app-sidebar class', () => {
        renderSidebar();
        expect(screen.getByTestId('sidebar-mobile-spacer')).toHaveClass('app-sidebar');
    });
});

describe('Sidebar – mobile rail (collapsed state)', () => {
    it('renders a chevron toggle button', () => {
        renderSidebar();
        const rail = screen.getByTestId('sidebar-mobile-rail');
        expect(within(rail).getByRole('button', { name: /open sidebar/i })).toBeInTheDocument();
    });

    it('shows 5 nav icon links in the rail', () => {
        renderSidebar();
        const rail = screen.getByTestId('sidebar-mobile-rail');
        expect(within(rail).getAllByRole('link')).toHaveLength(5);
    });

    it('includes a Past Plans link pointing at /past-plans', () => {
        renderSidebar();
        const rail = screen.getByTestId('sidebar-mobile-rail');
        expect(within(rail).getByRole('link', { name: /past plans/i })).toHaveAttribute(
            'href',
            '/past-plans'
        );
    });

    it('does not show nav item text labels in the collapsed rail', () => {
        renderSidebar();
        const rail = screen.getByTestId('sidebar-mobile-rail');
        expect(within(rail).queryByText('Meal Planner')).not.toBeInTheDocument();
        expect(within(rail).queryByText('My Recipes')).not.toBeInTheDocument();
    });

    it('overlay is present in the DOM but hidden (aria-hidden) when collapsed', () => {
        renderSidebar();
        const overlay = screen.getByTestId('sidebar-mobile-overlay');
        expect(overlay).toBeInTheDocument();
        expect(overlay).toHaveAttribute('aria-hidden', 'true');
    });

    it('does not show Sign Out in the collapsed rail', () => {
        renderSidebar();
        const rail = screen.getByTestId('sidebar-mobile-rail');
        expect(within(rail).queryByRole('button', { name: /sign out/i })).not.toBeInTheDocument();
    });
});

describe('Sidebar – expand/collapse behaviour', () => {
    it('clicking the chevron expands the overlay (aria-hidden becomes false)', async () => {
        const user = userEvent.setup();
        renderSidebar();
        await user.click(screen.getByRole('button', { name: /open sidebar/i }));
        expect(screen.getByTestId('sidebar-mobile-overlay')).toHaveAttribute('aria-hidden', 'false');
    });

    it('clicking the chevron again collapses the overlay', async () => {
        const user = userEvent.setup();
        renderSidebar();
        await user.click(screen.getByRole('button', { name: /open sidebar/i }));
        await user.click(screen.getByRole('button', { name: /close sidebar/i }));
        expect(screen.getByTestId('sidebar-mobile-overlay')).toHaveAttribute('aria-hidden', 'true');
    });

    it('clicking a nav link in the expanded overlay collapses it', async () => {
        const user = userEvent.setup();
        renderSidebar();
        await user.click(screen.getByRole('button', { name: /open sidebar/i }));
        const overlay = screen.getByTestId('sidebar-mobile-overlay');
        await user.click(within(overlay).getAllByRole('link')[0]);
        expect(screen.getByTestId('sidebar-mobile-overlay')).toHaveAttribute('aria-hidden', 'true');
    });

    it('clicking the backdrop collapses the overlay', async () => {
        const user = userEvent.setup();
        renderSidebar();
        await user.click(screen.getByRole('button', { name: /open sidebar/i }));
        await user.click(screen.getByTestId('sidebar-backdrop'));
        expect(screen.getByTestId('sidebar-mobile-overlay')).toHaveAttribute('aria-hidden', 'true');
    });

    it('pressing Escape collapses the overlay', async () => {
        const user = userEvent.setup();
        renderSidebar();
        await user.click(screen.getByRole('button', { name: /open sidebar/i }));
        await user.keyboard('{Escape}');
        expect(screen.getByTestId('sidebar-mobile-overlay')).toHaveAttribute('aria-hidden', 'true');
    });
});

describe('Sidebar – expanded overlay content', () => {
    it('shows nav item labels in the overlay', async () => {
        const user = userEvent.setup();
        renderSidebar();
        await user.click(screen.getByRole('button', { name: /open sidebar/i }));
        const overlay = screen.getByTestId('sidebar-mobile-overlay');
        expect(within(overlay).getByText('Meal Planner')).toBeInTheDocument();
        expect(within(overlay).getByText('Past Plans')).toBeInTheDocument();
        expect(within(overlay).getByText('My Recipes')).toBeInTheDocument();
    });

    it('shows the account footer (Sign Out) only in the expanded overlay, not in the rail', async () => {
        const user = userEvent.setup();
        renderSidebar();

        // Sign Out must not be present in the collapsed rail
        const rail = screen.getByTestId('sidebar-mobile-rail');
        expect(within(rail).queryByRole('button', { name: /sign out/i })).not.toBeInTheDocument();

        // Overlay is in DOM but aria-hidden before expanding
        expect(screen.getByTestId('sidebar-mobile-overlay')).toHaveAttribute('aria-hidden', 'true');

        await user.click(screen.getByRole('button', { name: /open sidebar/i }));
        const overlay = screen.getByTestId('sidebar-mobile-overlay');
        expect(within(overlay).getByRole('button', { name: /sign out/i })).toBeInTheDocument();
    });

    it('tapping a nav icon in the rail navigates without expanding the overlay', async () => {
        const user = userEvent.setup();
        renderSidebar();
        const rail = screen.getByTestId('sidebar-mobile-rail');
        const navLinks = within(rail).getAllByRole('link');
        await user.click(navLinks[0]);
        expect(screen.getByTestId('sidebar-mobile-overlay')).toHaveAttribute('aria-hidden', 'true');
    });
});
