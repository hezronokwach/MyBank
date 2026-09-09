import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/axiosInstance';

const RegisterForm = () => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [role, setRole] = useState('ROLE_USER');
    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();
        try {
            await api.post('/auth/register', { email, password, firstName, lastName, role });
            navigate('/login');
        } catch (error) {
            if (error.response && error.response.status === 409) {
                alert('Registration failed: Email already exists.');
            } else {
                alert('Registration failed. Please try again.');
            }
        }
    };

    return (
        <form onSubmit={handleSubmit} className="max-w-sm p-6 bg-white rounded-lg shadow-md">
            <h2 className="mb-4 text-2xl font-bold text-gray-800">Register</h2>
            <input type="email" placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} className="w-full p-2 mb-4 border rounded" required />
            <input type="password" placeholder="Password" value={password} onChange={(e) => setPassword(e.target.value)} className="w-full p-2 mb-4 border rounded" required />
            <input type="text" placeholder="First Name" value={firstName} onChange={(e) => setFirstName(e.target.value)} className="w-full p-2 mb-4 border rounded" required />
            <input type="text" placeholder="Last Name" value={lastName} onChange={(e) => setLastName(e.target.value)} className="w-full p-2 mb-4 border rounded" required />
            <select value={role} onChange={(e) => setRole(e.target.value)} className="w-full p-2 mb-4 border rounded">
                <option value="ROLE_USER">User</option>
                <option value="ROLE_ADMIN">Admin</option>
            </select>
            <button type="submit" className="w-full p-2 text-white bg-green-600 rounded hover:bg-green-700">Register</button>
        </form>
    );
};

export default RegisterForm;
