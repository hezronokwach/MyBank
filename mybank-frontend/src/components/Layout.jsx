import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import api from '../api/axiosInstance';

const Layout = ({ children }) => {
    const navigate = useNavigate();
    const [isAdmin, setIsAdmin] = useState(false);

    useEffect(() => {
        const fetchRole = async () => {
            try {
                const response = await api.get('/auth/me/role');
                setIsAdmin(response.data === 'ROLE_ADMIN');
            } catch (error) {
                console.error('Failed to fetch user role', error);
            }
        };
        fetchRole();
    }, []);

    const handleLogout = () => {
        localStorage.removeItem('jwt');
        navigate('/login');
    };

    return (
        <div className="min-h-screen bg-gray-50">
            <nav className="bg-white border-b border-gray-200">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                    <div className="flex justify-between h-16 items-center">
                        <Link to="/dashboard" className="text-2xl font-bold text-indigo-600">MyBank</Link>
                        <div className="flex items-center gap-6">
                            <Link to="/dashboard" className="text-gray-600 hover:text-indigo-600 transition font-medium">Dashboard</Link>
                            {isAdmin && <Link to="/admin" className="text-gray-600 hover:text-indigo-600 transition font-medium">Admin Panel</Link>}
                            <button onClick={handleLogout} className="px-4 py-2 text-red-600 bg-red-50 hover:bg-red-100 rounded-lg transition font-medium">Logout</button>
                        </div>
                    </div>
                </div>
            </nav>
            <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
                {children}
            </main>
        </div>
    );
};

export default Layout;
