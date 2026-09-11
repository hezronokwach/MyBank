import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../api/axiosInstance';
import { useNotification } from './notificationContext';

const RegisterForm = () => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [pin, setPin] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const navigate = useNavigate();
    const { notify } = useNotification();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setIsSubmitting(true);
        try {
            await api.post('/auth/register', { email, password, firstName, lastName, pin, role: 'ROLE_USER' });
            notify('Account created. Sign in to continue.');
            navigate('/login');
        } catch (error) {
            if (error.response && error.response.status === 409) {
                notify('An account with this email already exists.', 'error');
            } else {
                notify('We could not create your account. Please try again.', 'error');
            }
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <form onSubmit={handleSubmit} className="auth-form">
            <p className="eyebrow">MyBank</p>
            <h1>Create your account</h1>
            <p className="form-intro">A few details and you’ll be ready to get started.</p>
            <div className="form-grid">
                <div><label htmlFor="first-name">First name</label><input id="first-name" type="text" value={firstName} onChange={(e) => setFirstName(e.target.value)} autoComplete="given-name" required /></div>
                <div><label htmlFor="last-name">Last name</label><input id="last-name" type="text" value={lastName} onChange={(e) => setLastName(e.target.value)} autoComplete="family-name" required /></div>
            </div>
            <label htmlFor="register-email">Email address</label>
            <input id="register-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
            <label htmlFor="register-password">Password</label>
            <input id="register-password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" required />
            <label htmlFor="register-pin">PIN</label>
            <input id="register-pin" type="password" value={pin} onChange={(e) => setPin(e.target.value)} maxLength="4" required />
            <button type="submit" className="button button--primary" disabled={isSubmitting}>{isSubmitting ? 'Creating account…' : 'Create account'}</button>
            <p className="form-switch">Already have an account? <Link to="/login">Sign in</Link></p>
        </form>
    );
};

export default RegisterForm;
