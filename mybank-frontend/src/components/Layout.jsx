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

// The bar at the top of every signed-in page.
// Customers see Dashboard. Admins see their own two pages instead: Approvals and Assistant stats.
const Layout = ({ children }) => {
    const navigate = useNavigate();
    // null until we know who is signed in, so the wrong links never flash on screen
    const [role, setRole] = useState(savedRole);

    useEffect(() => {
        const fetchRole = async () => {
            try {
                const response = await api.get('/auth/me/role');
                savedRole = response.data;
                setRole(response.data);
            } catch (error) {
                console.error('Failed to fetch user role', error);
            }
        };
        fetchRole();
    }, []);

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
                {children}
            </main>
        </div>
    );
};

export default Layout;
