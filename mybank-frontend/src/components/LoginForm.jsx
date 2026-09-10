import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/axiosInstance';

const LoginForm = () => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const navigate = useNavigate();
    
    const handleSubmit = async (e) => {
        e.preventDefault();
        try {
            const response = await api.post('/auth/login', { email, password });
            localStorage.setItem('jwt', response.data.token);
            navigate('/dashboard');
        } catch (error) {
            alert('Login failed');
        }
    };

    return (
        <form onSubmit={handleSubmit} className="max-w-sm p-6 bg-white rounded-lg shadow-md">
            <h2 className="mb-4 text-2xl font-bold text-bank-heading">Login</h2>
            <input 
                type="email" 
                placeholder="Email" 
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full p-2 mb-4 border rounded" 
            />
            <input 
                type="password" 
                placeholder="Password" 
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full p-2 mb-4 border rounded" 
            />
            <button type="submit" className="w-full p-2 text-white bg-blue-600 rounded hover:bg-blue-700">Login</button>
        </form>
    );
};

export default LoginForm;
