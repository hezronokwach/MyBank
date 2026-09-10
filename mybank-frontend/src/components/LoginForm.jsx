import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../api/axiosInstance';
import { useNotification } from './notificationContext';

const LoginForm = () => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const navigate = useNavigate();
    const { notify } = useNotification();
    
    const handleSubmit = async (e) => {
        e.preventDefault();
        setIsSubmitting(true);
        try {
            const response = await api.post('/auth/login', { email, password });
            localStorage.setItem('jwt', response.data.token);
            navigate('/dashboard');
        } catch (error) {
            notify(error.response?.data?.message || 'We could not sign you in. Check your email and password.', 'error');
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <form onSubmit={handleSubmit} className="auth-form">
            <p className="eyebrow">MyBank</p>
            <h1>Welcome back</h1>
            <p className="form-intro">Sign in to manage your accounts securely.</p>
            <label htmlFor="login-email">Email address</label>
            <input 
                id="login-email"
                type="email" 
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                required
            />
            <label htmlFor="login-password">Password</label>
            <input 
                id="login-password"
                type="password" 
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
            />
            <button type="submit" className="button button--primary" disabled={isSubmitting}>{isSubmitting ? 'Signing in…' : 'Sign in'}</button>
            <p className="form-switch">New to MyBank? <Link to="/register">Create an account</Link></p>
        </form>
    );
};

export default LoginForm;
