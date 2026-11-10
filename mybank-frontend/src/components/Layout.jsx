import { useState, useEffect } from 'react';
import { useNavigate, Link, NavLink } from 'react-router-dom';
import api from '../api/axiosInstance';

// The link style: filled when it is the page you are on
function linkClass({ isActive }) {
    if (isActive) {
        return 'app-nav__link app-nav__link--active';
    }
    return 'app-nav__link';
}

// Remembers the role between pages, so the links don't disappear and come back on every page change.
// It is cleared on sign out.
let savedRole = null;

// Can this role see a page meant for "access"?
//   "customer" pages: only customers (admins never have bank accounts)
//   "admin" pages:    only admins
//   no access given:  anyone who is signed in
function isAllowed(access, role) {
    if (access === 'customer') {
        return role === 'ROLE_USER';
    }
    if (access === 'admin') {
        return role === 'ROLE_ADMIN';
    }
    return role !== null;
}

// The bar at the top of every signed-in page.
// Customers see Dashboard. Admins see their own two pages instead: Approvals and Assistant stats.
// A page in the wrong place is sent to the right home page, and a signed-out visitor to the sign-in page.
const Layout = ({ children, access }) => {
    const navigate = useNavigate();
    // null until we know who is signed in, so the wrong links never flash on screen
    const [role, setRole] = useState(savedRole);

    useEffect(() => {
        const fetchRole = async () => {
            if (!localStorage.getItem('jwt')) {
                navigate('/login', { replace: true });
                return;
            }
            try {
                const response = await api.get('/auth/me/role');
                savedRole = response.data;
                setRole(response.data);
            } catch (error) {
                // The token has expired or is not valid any more
                console.error('Failed to fetch user role', error);
                localStorage.removeItem('jwt');
                savedRole = null;
                navigate('/login', { replace: true });
            }
        };
        fetchRole();
    }, [navigate]);

    // Wrong page for this role: go to the right home page
    useEffect(() => {
        if (role === null || isAllowed(access, role)) {
            return;
        }
        if (role === 'ROLE_ADMIN') {
            navigate('/admin', { replace: true });
        } else {
            navigate('/dashboard', { replace: true });
        }
    }, [access, role, navigate]);

    const handleLogout = () => {
        localStorage.removeItem('jwt');
        savedRole = null;
        navigate('/login');
    };

    const isAdmin = role === 'ROLE_ADMIN';

    let homePath = '/dashboard';
    if (isAdmin) {
        homePath = '/admin';
    }

    return (
        <div className="app-shell">
            <nav className="app-nav">
                <div className="app-nav__inner">
                    <Link to={homePath} className="brand">MyBank</Link>
                    <div className="app-nav__links">
                        {role === 'ROLE_USER' && (
                            <NavLink to="/dashboard" className={linkClass}>Dashboard</NavLink>
                        )}
                        {isAdmin && (
                            <>
                                <span className="app-nav__role">Admin</span>
                                {/* "end" so this link is not also marked active on /admin/stats */}
                                <NavLink to="/admin" end className={linkClass}>Approvals</NavLink>
                                <NavLink to="/admin/stats" className={linkClass}>Assistant stats</NavLink>
                            </>
                        )}
                        <button type="button" onClick={handleLogout} className="app-nav__signout">Sign out</button>
                    </div>
                </div>
            </nav>
            <main className="app-main">
                {/* The page only shows once we know it is the right place for this role */}
                {isAllowed(access, role) && children}
            </main>
        </div>
    );
};

export default Layout;
